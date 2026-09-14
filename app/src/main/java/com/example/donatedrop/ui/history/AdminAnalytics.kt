package com.example.donatedrop.ui.history

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.donatedrop.adapters.ReportsAdapter
import com.example.donatedrop.databinding.FragmentAdminAnalyticsBinding
import com.example.donatedrop.models.Report
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.tasks.Tasks
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AdminAnalytics : Fragment() {

    private var _binding: FragmentAdminAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    private val humanDateTime = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private val humanDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    private val colName = "DonationHistory"          // <-- change to "DnoationHistory" if that's what you have
    private val statusField = "Status"
    private val statusCompletedValue = "completed"
    private val timestampField = "CompletedAt"

    private val usersCollection = "users"            // users collection name
    private val createdRequestsCollection = "Blood Requests"
    private val createdAtField = "CreatedAt"        // request created timestamp field name
    private val completedAtField = "CompletedAt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // initial placeholders
        binding.tvTotalValue.text = "-"
        binding.tvActiveValue.text = "-"

        // Date pickers
        binding.dateFrom.setOnClickListener {
            showMaterialDatePicker { dateStr ->
                binding.dateFrom.setText(dateStr)
                binding.fromLayout.isHintEnabled = false
            }
        }
        binding.dateTo.setOnClickListener {
            showMaterialDatePicker { dateStr ->
                binding.dateTo.setText(dateStr)
                binding.toLayout.isHintEnabled = false
            }
        }

        // Filter button
        binding.btnFilter.setOnClickListener {
            val fromStr = binding.dateFrom.text.toString().trim()
            val toStr = binding.dateTo.text.toString().trim()
            if (fromStr.isEmpty() || toStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please select both From and To dates", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                queryCompletedRequestsAndDrawChart(fromStr, toStr)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Invalid dates: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Chart
        setupChart()
        drawEmptyChartPlaceholder()

        // Recycler (sample)
        val reports = listOf(
            Report("May 2025", "Summary of donations and requests for May 2025"),
            Report("April 2025", "Summary of donations and requests for April 2025"),
            Report("March 2025", "Summary of donations and requests for March 2025")
        )
        val adapter = ReportsAdapter(reports) { _ -> /* noop */ }
        binding.reportsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.reportsRecycler.adapter = adapter

        // Generate PDF button
        binding.btnGenerate.setOnClickListener {
            generateMonthlyReport()
        }
    }

    private fun showMaterialDatePicker(onPicked: (String) -> Unit) {
        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val pickedDate = Date(selection as Long)
            val formatted = dateFormat.format(pickedDate)
            onPicked(formatted)
        }
        picker.show(childFragmentManager, "MATERIAL_DATE_PICKER")
    }

    private fun setupChart() {
        val chart = binding.lineChart
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.xAxis.isEnabled = false
        chart.axisRight.isEnabled = false
    }

    private fun drawEmptyChartPlaceholder() {
        val entries = listOf(Entry(0f, 0f))
        val set = LineDataSet(entries, "Completed Requests")
        set.setDrawValues(false)
        set.setDrawCircles(true)
        set.lineWidth = 2f
        binding.lineChart.data = LineData(set)
        binding.lineChart.invalidate()
    }

    /**
     * When filtering: fetch completed requests and users in parallel,
     * update chart + tvTotalValue (completed count) and tvActiveValue (users count).
     */
    private fun queryCompletedRequestsAndDrawChart(fromStr: String, toStr: String) {
        val fromDate = dateFormat.parse(fromStr) ?: run {
            Toast.makeText(requireContext(), "Invalid from date", Toast.LENGTH_SHORT).show()
            return
        }
        val toDate = dateFormat.parse(toStr) ?: run {
            Toast.makeText(requireContext(), "Invalid to date", Toast.LENGTH_SHORT).show()
            return
        }

        val startCal = Calendar.getInstance().apply {
            time = fromDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            time = toDate
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }

        val startTs = Timestamp(startCal.time)
        val endTs = Timestamp(endCal.time)

        val diffDays = TimeUnit.MILLISECONDS.toDays(endCal.timeInMillis - startCal.timeInMillis).toInt()
        if (diffDays < 0) {
            Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show()
            return
        }
        if (diffDays > 365) {
            Toast.makeText(requireContext(), "Please choose a range smaller than 1 year", Toast.LENGTH_SHORT).show()
        }

        binding.btnFilter.isEnabled = false
        binding.btnFilter.text = "Filtering..."

        val db = FirebaseFirestore.getInstance()
        val qCompleted = db.collection(colName)
            .whereEqualTo(statusField, statusCompletedValue)
            .whereGreaterThanOrEqualTo(timestampField, startTs)
            .whereLessThanOrEqualTo(timestampField, endTs)
            .orderBy(timestampField)
            .get()

        val qUsers = db.collection(usersCollection)
            .whereGreaterThanOrEqualTo(createdAtField, startTs)
            .whereLessThanOrEqualTo(createdAtField, endTs)
            .get()

        Tasks.whenAllSuccess<QuerySnapshot>(qCompleted, qUsers)
            .addOnSuccessListener { results ->
                val completedSnap = results[0]
                val usersSnap = results[1]

                // set users count
                val usersCount = usersSnap.size()
                binding.tvActiveValue.text = usersCount.toString()

                // aggregate per-day completed counts for the chart
                val counts = HashMap<Int, Int>()
                for (doc in completedSnap.documents) {
                    val ts = doc.getTimestamp(timestampField) ?: continue
                    val dayIndex = TimeUnit.MILLISECONDS.toDays(ts.toDate().time - startCal.timeInMillis).toInt()
                    counts[dayIndex] = (counts[dayIndex] ?: 0) + 1
                }

                val entries = ArrayList<Entry>()
                var totalCompleted = 0
                for (i in 0..diffDays) {
                    val c = counts[i] ?: 0
                    entries.add(Entry(i.toFloat(), c.toFloat()))
                    totalCompleted += c
                }

                val set = LineDataSet(entries, "Completed Requests")
                set.setDrawValues(false); set.setDrawCircles(true); set.lineWidth = 2f
                binding.lineChart.data = LineData(set)
                binding.lineChart.invalidate()

                binding.tvTotalValue.text = totalCompleted.toString()

                binding.btnFilter.isEnabled = true
                binding.btnFilter.text = "Filter"
            }
            .addOnFailureListener { e ->
                binding.btnFilter.isEnabled = true
                binding.btnFilter.text = "Filter"
                Toast.makeText(requireContext(), "Failed to load analytics: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun getRangeForReport(): Pair<Timestamp, Timestamp> {
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()
        val fromStr = binding.dateFrom.text.toString().trim()
        val toStr = binding.dateTo.text.toString().trim()

        if (fromStr.isNotEmpty() && toStr.isNotEmpty()) {
            try {
                val fromDate = dateFormat.parse(fromStr)!!
                val toDate = dateFormat.parse(toStr)!!
                startCal.time = fromDate; endCal.time = toDate
            } catch (e: Exception) {
                // fallback to current month
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
                endCal.time = startCal.time; endCal.add(Calendar.MONTH, 1); endCal.add(Calendar.MILLISECOND, -1)
                return Pair(Timestamp(startCal.time), Timestamp(endCal.time))
            }
        } else {
            startCal.set(Calendar.DAY_OF_MONTH, 1)
            startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
            endCal.time = startCal.time; endCal.add(Calendar.MONTH, 1); endCal.add(Calendar.MILLISECOND, -1)
            return Pair(Timestamp(startCal.time), Timestamp(endCal.time))
        }

        startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
        endCal.set(Calendar.HOUR_OF_DAY, 23); endCal.set(Calendar.MINUTE, 59); endCal.set(Calendar.SECOND, 59); endCal.set(Calendar.MILLISECOND, 999)
        return Pair(Timestamp(startCal.time), Timestamp(endCal.time))
    }

    /**
     * Run three queries (users joined, requests created from Blood Requests, requests completed from DonationHistory),
     * gather small details lists for created/completed requests, then build a paginated PDF.
     */
    private fun generateMonthlyReport() {
        val (startTs, endTs) = getRangeForReport()
        val db = FirebaseFirestore.getInstance()

        // Users query
        val qUsers = db.collection(usersCollection)
            .whereGreaterThanOrEqualTo(createdAtField, startTs)
            .whereLessThanOrEqualTo(createdAtField, endTs)
            .get()

        // Created requests query (from Blood Requests)
        val qRequestsCreated = db.collection(createdRequestsCollection)
            .whereGreaterThanOrEqualTo(createdAtField, startTs)
            .whereLessThanOrEqualTo(createdAtField, endTs)
            .get()

        // Completed requests query (DonationHistory)
        val qRequestsCompleted = db.collection(colName)
            .whereEqualTo(statusField, statusCompletedValue)
            .whereGreaterThanOrEqualTo(completedAtField, startTs)
            .whereLessThanOrEqualTo(completedAtField, endTs)
            .get()

        binding.btnGenerate.isEnabled = false
        binding.btnGenerate.text = "Generating..."

        Tasks.whenAllSuccess<QuerySnapshot>(qUsers, qRequestsCreated, qRequestsCompleted)
            .addOnSuccessListener { results ->
                val usersSnap = results[0]
                val createdSnap = results[1]
                val completedSnap = results[2]

                val usersCount = usersSnap.size()
                val createdCount = createdSnap.size()
                val completedCount = completedSnap.size()

                // Build detailed lists
                val usersDetails = ArrayList<Pair<String, String>>() // Pair(name,email)
                for (doc in usersSnap.documents) {
                    val data = doc.data
                    val name = readStringField(data, listOf("fullName", "Name")) ?: doc.id
                    val email = readStringField(data, listOf("Email","userEmail")) ?: "-"
                    usersDetails.add(Pair(name, email))
                    if (usersDetails.size >= 500) break // safety cap
                }

                val createdDetails = ArrayList<List<String>>() // list of rows: [title/id, requester, bloodType, createdAt, location]
                for (doc in createdSnap.documents) {
                    val data = doc.data
                    val title = readStringField(data, listOf("requestTitle", "title", "name")) ?: doc.id
                    val requester = readStringField(data, listOf("requesterName", "userName", "Name")) ?: "-"
                    val bloodType = readStringField(data, listOf("bloodType", "Blood Group")) ?: "-"
                    val createdAt = doc.getTimestamp(createdAtField)?.toDate()?.let { humanDate.format(it) } ?: "-"
                    val location = readStringField(data, listOf("location", "City")) ?: "-"
                    createdDetails.add(listOf(title, requester, bloodType, createdAt, location))
                    if (createdDetails.size >= 500) break
                }

                val completedDetails = ArrayList<List<String>>() // rows: [title/id, requester, donor, bloodType, completedAt, location]
                for (doc in completedSnap.documents) {
                    val data = doc.data
                    val title = readStringField(data, listOf("requestTitle", "title", "Name")) ?: doc.id
                    val requester = readStringField(data, listOf("Receiver's Name", "userName", "name")) ?: "-"
                    val donor = readStringField(data, listOf("donorName", "Responder Name", "donor")) ?: "-"
                    val bloodType = readStringField(data, listOf("bloodType", "Blood Group"))?: "-"
                    val completedAt = doc.getTimestamp(completedAtField)?.toDate()?.let { humanDateTime.format(it) } ?: "-"
                    val location = readStringField(data, listOf("location", "City", "address")) ?: "-"
                    completedDetails.add(listOf(title, requester, donor, bloodType, completedAt, location))
                    if (completedDetails.size >= 500) break
                }

                // Update UI quick totals
                binding.tvTotalValue.text = completedCount.toString()
                binding.tvActiveValue.text = usersCount.toString()

                // Create a well-organized multi-page PDF
                try {
                    val file = createOrganizedReportPdf(
                        startTs.toDate(), endTs.toDate(),
                        usersCount, createdCount, completedCount,
                        usersDetails, createdDetails, completedDetails
                    )
                    Toast.makeText(requireContext(), "Report saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    openPdfWithProvider(file)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to create PDF: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    binding.btnGenerate.isEnabled = true
                    binding.btnGenerate.text = "Generate New Report"
                }
            }
            .addOnFailureListener { exc ->
                binding.btnGenerate.isEnabled = true
                binding.btnGenerate.text = "Generate New Report"
                Toast.makeText(requireContext(), "Failed to fetch data: ${exc.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Safe helper to read string-like fields from doc data
    private fun readStringField(data: Map<String, Any>?, keys: List<String>): String? {
        if (data == null) return null
        for (k in keys) {
            val v = data[k] ?: continue
            if (v is String && v.isNotBlank()) return v
        }
        return null
    }

    /**
     * Create a multi-page organized PDF:
     * - Summary page with totals
     * - Completed Requests table-style listing
     * - New Users listing
     */
    private fun createOrganizedReportPdf(
        fromDate: Date, toDate: Date,
        usersCount: Int, createdCount: Int, completedCount: Int,
        usersDetails: List<Pair<String, String>>,
        createdDetails: List<List<String>>,
        completedDetails: List<List<String>>
    ): File {
        val docsDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: requireContext().filesDir
        if (!docsDir.exists()) docsDir.mkdirs()

        val fileName = "DonateDrop_Report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        val outFile = File(docsDir, fileName)

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4-ish
        val left = 36f
        val right = 36f
        val topStart = 56f
        val lineHeight = 16f
        val bottomMargin = 48f
        val usableWidth = pageInfo.pageWidth - left - right

        fun startNewPage(titleSuffix: String? = null): PdfDocument.Page {
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            val paintHeader = Paint().apply { textSize = 18f; isFakeBoldText = true }
            val title = if (titleSuffix == null) "DonateDrop — Monthly Report" else "DonateDrop — $titleSuffix"
            canvas.drawText(title, left, topStart, paintHeader)
            val paintSub = Paint().apply { textSize = 12f; isFakeBoldText = false }
            canvas.drawText("From: ${dateFormat.format(fromDate)}   To: ${dateFormat.format(toDate)}", left, topStart + 22f, paintSub)
            return page
        }

        // 1) Summary page
        var page = startNewPage("Summary")
        var canvas = page.canvas
        var paint = Paint()
        paint.textSize = 14f; paint.isFakeBoldText = false
        var y = topStart + 50f

        canvas.drawText("Summary", left, y, paint.apply { isFakeBoldText = true; textSize = 16f })
        y += lineHeight + 8f
        paint.apply { isFakeBoldText = false; textSize = 13f }
        canvas.drawText("Total new users: $usersCount", left, y, paint); y += lineHeight
        canvas.drawText("Total requests created: $createdCount", left, y, paint); y += lineHeight
        canvas.drawText("Total requests completed: $completedCount", left, y, paint); y += lineHeight + 8f

        canvas.drawText("Details sections follow on next pages.", left, y, paint); y += lineHeight + 8f
        pdf.finishPage(page)

        // Helper for pagination and drawing lines of text
        fun openPageAndCanvas(sectionTitle: String): Triple<PdfDocument.Page, android.graphics.Canvas, Float> {
            val p = startNewPage(sectionTitle)
            val c = p.canvas
            var yy = topStart + 40f
            val pTitle = Paint().apply { textSize = 15f; isFakeBoldText = true }
            c.drawText(sectionTitle, left, yy, pTitle)
            yy += lineHeight + 6f
            return Triple(p, c, yy)
        }

        // 2) Completed Requests section (table-like)
        var (p2, c2, yy2) = openPageAndCanvas("Completed Requests")
        paint = Paint().apply { textSize = 11f; isFakeBoldText = false }
        // Header row
        val hdrPaint = Paint().apply { textSize = 11f; isFakeBoldText = true }
        c2.drawText("Title / ID", left, yy2, hdrPaint)
        c2.drawText("Requester", left + usableWidth * 0.32f, yy2, hdrPaint)
        c2.drawText("Donor", left + usableWidth * 0.52f, yy2, hdrPaint)
        c2.drawText("Completed At", left + usableWidth * 0.72f, yy2, hdrPaint)
        yy2 += lineHeight

        if (completedDetails.isEmpty()) {
            c2.drawText("- None -", left, yy2, paint); yy2 += lineHeight
        } else {
            for (row in completedDetails) {
                if (yy2 + bottomMargin > pageInfo.pageHeight) {
                    pdf.finishPage(p2)
                    val opened = openPageAndCanvas("Completed Requests (cont.)")
                    p2 = opened.first; c2 = opened.second; yy2 = opened.third
                    // redraw header on continued page
                    c2.drawText("Title / ID", left, yy2, hdrPaint)
                    c2.drawText("Requester", left + usableWidth * 0.32f, yy2, hdrPaint)
                    c2.drawText("Donor", left + usableWidth * 0.52f, yy2, hdrPaint)
                    c2.drawText("Completed At", left + usableWidth * 0.72f, yy2, hdrPaint)
                    yy2 += lineHeight
                }
                // row fields: title, requester, donor, bloodType, completedAt, location
                val title = row.getOrNull(0) ?: "-"
                val requester = row.getOrNull(1) ?: "-"
                val donor = row.getOrNull(2) ?: "-"
                val completedAt = row.getOrNull(4) ?: "-"
                // draw truncated values to fit columns
                c2.drawText(shorten(title, 36), left, yy2, paint)
                c2.drawText(shorten(requester, 20), left + usableWidth * 0.32f, yy2, paint)
                c2.drawText(shorten(donor, 18), left + usableWidth * 0.52f, yy2, paint)
                c2.drawText(shorten(completedAt, 18), left + usableWidth * 0.72f, yy2, paint)
                yy2 += lineHeight
            }
        }
        pdf.finishPage(p2)

        // 3) Created Requests section
        var (p3, c3, yy3) = openPageAndCanvas("Created Requests")
        paint = Paint().apply { textSize = 11f; isFakeBoldText = false }
        val hdrPaint2 = Paint().apply { textSize = 11f; isFakeBoldText = true }
        c3.drawText("Title / ID", left, yy3, hdrPaint2)
        c3.drawText("Requester", left + usableWidth * 0.36f, yy3, hdrPaint2)
        c3.drawText("Blood Type", left + usableWidth * 0.6f, yy3, hdrPaint2)
        c3.drawText("Created At", left + usableWidth * 0.76f, yy3, hdrPaint2)
        yy3 += lineHeight

        if (createdDetails.isEmpty()) {
            c3.drawText("- None -", left, yy3, paint); yy3 += lineHeight
        } else {
            for (row in createdDetails) {
                if (yy3 + bottomMargin > pageInfo.pageHeight) {
                    pdf.finishPage(p3)
                    val opened = openPageAndCanvas("Created Requests (cont.)")
                    p3 = opened.first; c3 = opened.second; yy3 = opened.third
                    c3.drawText("Title / ID", left, yy3, hdrPaint2)
                    c3.drawText("Requester", left + usableWidth * 0.36f, yy3, hdrPaint2)
                    c3.drawText("Blood Type", left + usableWidth * 0.6f, yy3, hdrPaint2)
                    c3.drawText("Created At", left + usableWidth * 0.76f, yy3, hdrPaint2)
                    yy3 += lineHeight
                }
                val title = row.getOrNull(0) ?: "-"
                val requester = row.getOrNull(1) ?: "-"
                val bloodType = row.getOrNull(2) ?: "-"
                val createdAt = row.getOrNull(3) ?: "-"
                c3.drawText(shorten(title, 36), left, yy3, paint)
                c3.drawText(shorten(requester, 20), left + usableWidth * 0.36f, yy3, paint)
                c3.drawText(shorten(bloodType, 8), left + usableWidth * 0.6f, yy3, paint)
                c3.drawText(shorten(createdAt, 14), left + usableWidth * 0.76f, yy3, paint)
                yy3 += lineHeight
            }
        }
        pdf.finishPage(p3)

        // 4) New Users section (name + email)
        var (p4, c4, yy4) = openPageAndCanvas("New Users")
        paint = Paint().apply { textSize = 11f; isFakeBoldText = false }
        val hdrPaint3 = Paint().apply { textSize = 11f; isFakeBoldText = true }
        c4.drawText("Name", left, yy4, hdrPaint3)
        c4.drawText("Email", left + usableWidth * 0.5f, yy4, hdrPaint3)
        yy4 += lineHeight

        if (usersDetails.isEmpty()) {
            c4.drawText("- None -", left, yy4, paint); yy4 += lineHeight
        } else {
            for (pair in usersDetails) {
                if (yy4 + bottomMargin > pageInfo.pageHeight) {
                    pdf.finishPage(p4)
                    val opened = openPageAndCanvas("New Users (cont.)")
                    p4 = opened.first; c4 = opened.second; yy4 = opened.third
                    c4.drawText("Name", left, yy4, hdrPaint3)
                    c4.drawText("Email", left + usableWidth * 0.5f, yy4, hdrPaint3)
                    yy4 += lineHeight
                }
                c4.drawText(shorten(pair.first, 30), left, yy4, paint)
                c4.drawText(shorten(pair.second, 36), left + usableWidth * 0.5f, yy4, paint)
                yy4 += lineHeight
            }
        }
        // Footer
        paint.textSize = 10f
        c4.drawText("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", left, pageInfo.pageHeight - 20f, paint)
        pdf.finishPage(p4)

        // write file
        FileOutputStream(outFile).use { stream -> pdf.writeTo(stream) }
        pdf.close()
        return outFile
    }

    // Shorten string safely for PDF columns
    private fun shorten(s: String, maxChars: Int): String {
        return if (s.length <= maxChars) s else s.substring(0, maxChars - 3) + "..."
    }

    private fun openPdfWithProvider(file: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open PDF (viewer not installed or FileProvider not configured).", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}