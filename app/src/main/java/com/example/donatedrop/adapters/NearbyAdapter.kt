package com.example.donatedrop

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.donatedrop.models.NearbyUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NearbyAdapter : RecyclerView.Adapter<NearbyAdapter.VH>() {

    private val items = ArrayList<NearbyUser>()

    fun submitList(list: List<NearbyUser>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun timeAgo(date: Date?): String {
        if (date == null) return "Not updated"

        val now = System.currentTimeMillis()
        val then = date.time
        var diff = now - then

        if (diff < 0) {
            // future timestamp — treat as just now
            return "just now"
        }

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 1) return "just now"
        if (minutes < 60) {
            return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        }

        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) {
            return if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days < 7) {
            return if (days == 1L) "1 day ago" else "$days days ago"
        }
        // older than 7 days — show short date
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return fmt.format(date)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_nearby_user, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.name.text = item.name
        holder.distance.text = String.format("%.1f km Away", item.distanceMeters / 1000.0)
        holder.blood.text = item.bloodGroup
        holder.available.text = timeAgo(item.lastupdatedat)
        holder.contactBtn.setOnClickListener {
            // handle contact click (open profile or start chat/call)

            showContactDialog(holder.itemView.context, item.name, item.phone)
        }

        if (item.image != null) {
            Glide.with(holder.itemView.context)
                .load(item.image)
                .circleCrop()
                .into(holder.Avatar)

        }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val distance: TextView = view.findViewById(R.id.tvDistance)
        val blood: TextView = view.findViewById(R.id.tvBlood)
        val contactBtn: Button = view.findViewById(R.id.btnContact)
        val available: TextView = view.findViewById(R.id.tvAvailable)
        val Avatar: ImageView = view.findViewById(R.id.imgAvatar)
    }

    fun showContactDialog(ctx: Context, displayName: String?, rawPhone: String?) {
        val name = displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
        val phoneRaw = rawPhone?.trim() ?: ""

        // Inflate custom view
        val inflater = LayoutInflater.from(ctx)
        val view = inflater.inflate(R.layout.dialog_contact_nearby, null)

        val tvName: TextView = view.findViewById(R.id.tvContactName)
        val tvPhone: TextView = view.findViewById(R.id.tvContactPhone)
        val btnCall: Button = view.findViewById(R.id.btnCallContact)
        val btnCancel: Button = view.findViewById(R.id.btnCancelContact)

        tvName.text = name
        tvPhone.text = if (phoneRaw.isNotBlank()) phoneRaw else "No phone number"

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnCall.setOnClickListener {
            if (phoneRaw.isBlank()) {
                Toast.makeText(ctx, "No phone number available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Sanitize phone number a bit (remove spaces). Do NOT remove +.
            val sanitized = phoneRaw.replace(" ", "").replace("-", "").trim()

            // Use Uri.fromParts to be safe with special chars
            val uri = Uri.fromParts("tel", sanitized, null)
            val intent = Intent(Intent.ACTION_DIAL).apply { data = uri }

            try {
                ctx.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // unlikely on normal Android devices, but handle gracefully
                Toast.makeText(ctx, "No dialer app found", Toast.LENGTH_SHORT).show()
            }

            // Optionally close dialog:
            dialog.dismiss()
        }

        dialog.show()
    }
}
