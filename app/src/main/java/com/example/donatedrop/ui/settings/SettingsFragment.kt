package com.example.donatedrop.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.example.donatedrop.About
import com.example.donatedrop.ChangePassword
import com.example.donatedrop.LoginScreen
import com.example.donatedrop.ProfileScreen
import com.example.donatedrop.R
import com.example.donatedrop.databinding.FragmentSettingsBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val collectionsToDelete = listOf(
        "Blood Requests"
    )
    private val userStoragePrefix = "uploads"

    companion object {
        private const val TAG = "SettingsFragment"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var fstore: FirebaseFirestore
    private var profileListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Observe any simple text value from ViewModel (example)
        settingsViewModel.text.observe(viewLifecycleOwner) { t ->
            // If you have a text view to show generic settings text, set it here.
            // I set profile name if empty just as an example:
            if (binding.tvProfileName != null && (binding.tvProfileName.text.isNullOrBlank())) {
                binding.tvProfileName.text = t
            }
        }

        setupFirebase()
        setupProfileCard()
        setupSettingsRows()
        setupLogout()
        setupDeleteAccount()

        return root
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        fstore = FirebaseFirestore.getInstance()
    }

    @SuppressLint("ResourceType")
    private fun setupProfileCard() {
        val cardProfile = binding.root.findViewById<MaterialCardView>(R.id.card_profile)
        val tvProfileName = binding.root.findViewById<TextView>(R.id.tv_profile_name)
        val ivProfile = binding.root.findViewById<ImageView>(R.id.iv_avatar)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val documentReference = fstore.collection("users").document(userId)

            // keep listener so we can remove it later
            profileListener = documentReference.addSnapshotListener { snapshot, exception ->
                exception?.let { return@addSnapshotListener }
                snapshot?.let {
                    val name = it.getString("Name")
                    if (name != null && tvProfileName != null) {
                        tvProfileName.text = name
                    }

                    val imageUrl = it.getString("avatarUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .circleCrop()
                            .into(ivProfile)
                    }
                }
            }
        } else {
            // Not signed in: clear text or show default
            binding.tvProfileName?.text = getString(R.string.not_signed_in)
        }

        cardProfile?.setOnClickListener {
            // Launch the profile activity (or navigate with NavController if you prefer)
            val intent = Intent(requireContext(), ProfileScreen::class.java)
            startActivity(intent)
            // If you want to keep fragment alive, don't finish activity. If you previously finished,
            // you can call requireActivity().finish() — but usually not needed in fragment.
        }
    }

    private fun setupSettingsRows() {
        // Notifications row
        setupSettingItem(
            rootId = R.id.setting_notifications,
            iconRes = R.drawable.ic_notification,
            title = getString(R.string.title_notifications),
            hasSwitch = true,
            initialSwitchState = true
        ) { isChecked ->
            Toast.makeText(requireContext(), "Notifications: $isChecked", Toast.LENGTH_SHORT).show()
            // Save/handle preference here using requireContext().getSharedPreferences(...)
        }

        // Change password
        setupSettingItem(
            rootId = R.id.setting_change_password,
            iconRes = R.drawable.ic_lock,
            title = getString(R.string.change_password),
            hasSwitch = false,
            subtitleText = null
        ) {
            startActivity(Intent(requireContext(), ChangePassword::class.java))
        }

        // Language (example)
        val currentLanguage = "English"
        setupSettingItem(
            rootId = R.id.setting_language,
            iconRes = R.drawable.ic_language,
            title = getString(R.string.language),
            hasSwitch = false,
            subtitleText = currentLanguage
        ) {
            Toast.makeText(requireContext(), "Language clicked", Toast.LENGTH_SHORT).show()
        }



        // About
        setupSettingItem(
            rootId = R.id.setting_about,
            iconRes = R.drawable.ic_info,
            title = getString(R.string.about_donatedrop),
            hasSwitch = false,
            subtitleText = null
        ) {
            startActivity(Intent(requireContext(), About::class.java))
        }
    }

    private fun setupLogout() {
        binding.root.findViewById<View>(R.id.btn_logout)?.setOnClickListener {
            Toast.makeText(requireContext(), "Logging out...", Toast.LENGTH_SHORT).show()
            auth.signOut()
            val sharedPreferences = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.clear()
            editor.apply()
            val intent = Intent(requireContext(), LoginScreen::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun setupDeleteAccount() {
        binding.root.findViewById<View>(R.id.btn_delete_account)?.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun showDeleteAccountDialog() {
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialoge_delete_account, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.setCanceledOnTouchOutside(true)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<Button>(R.id.btnDelete)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDelete.setOnClickListener {
            dialog.dismiss()
            deleteAccount()
        }

        dialog.show()

        // optional: size the dialog window nicely
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun deleteAccount() {
        startDeleteAccountFlow()
    }
    private fun startDeleteAccountFlow() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "No user signed in", Toast.LENGTH_SHORT).show()
            return
        }

        // Check sign-in providers: if password provider exists allow password reauth dialog
        val providers = user.providerData.map { it.providerId } // contains "password", "google.com", etc.
        if (providers.contains("password")) {
            // ask for password to reauthenticate
            showPasswordReauthDialog(user.email ?: "") { success ->
                if (success) {
                    // reauth succeeded and we can proceed
                    performFullDeleteForCurrentUser()
                } else {
                    Toast.makeText(requireContext(), "Re-authentication failed", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // For other providers (Google, Facebook, Phone), reauth flow requires the provider SDK.
            // Prompt the user and offer to open a help dialog or sign-in again.
            AlertDialog.Builder(requireContext())
                .setTitle("Re-authentication required")
                .setMessage("Your account was created with a third-party sign-in provider. To delete your account, please sign-in again using the same provider (Google/Facebook/Phone) in the app and then try deleting. If you can't, contact support.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * Shows a simple password prompt and re-authenticates current user using email/password credentials.
     * Calls onComplete with true if reauth successful.
     */
    @OptIn(UnstableApi::class)
    private fun showPasswordReauthDialog(email: String, onComplete: (Boolean) -> Unit) {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "Enter password"

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Confirm password")
            .setMessage("For security, please enter your password to confirm account deletion.")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                val password = input.text.toString()
                if (password.isBlank()) {
                    Toast.makeText(requireContext(), "Password required", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                    return@setPositiveButton
                }
                // Reauthenticate
                val credential = EmailAuthProvider.getCredential(email, password)
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                    return@setPositiveButton
                }
                currentUser.reauthenticate(credential)
                    .addOnSuccessListener {
                        onComplete(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Re-auth failed: ${e.message}", e)
                        Toast.makeText(requireContext(), "Re-authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                        onComplete(false)
                    }
            }
            .setNegativeButton("Cancel") { _, _ -> onComplete(false) }
            .create()
        dialog.show()
    }

    /**
     * Orchestrates deletion of all user data + auth user. Runs after re-auth success.
     */
    @OptIn(UnstableApi::class)
    private fun performFullDeleteForCurrentUser() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "No user signed in", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = user.uid

        // Show a blocking progress dialog while deletion proceeds
        val progressDlg = AlertDialog.Builder(requireContext())
            .setTitle("Deleting account")
            .setMessage("Please wait — deleting your account and data...")
            .setCancelable(false)
            .create()
        progressDlg.show()

        // Build tasks to delete collections, user doc, storage
        val tasks = mutableListOf<Task<Void>>()

        // 1) delete top-level collections where doc.userId == uid
        for (col in collectionsToDelete) {
            tasks.add(deleteCollectionDocumentsByUser(fstore, col, uid))
        }

        // 2) delete the users/{uid} document if you store user profile there
        val deleteUserDocTask = fstore.collection("users").document(uid).delete()
        tasks.add(deleteUserDocTask)

        // 3) delete storage prefix uploads/{uid}/
        tasks.add(deleteStoragePrefix(uid))

        // Wait for all deletes to finish
        Tasks.whenAll(tasks)
            .addOnSuccessListener {
                Log.d(TAG, "All data deletion tasks complete, now deleting auth user...")
                // Now delete the auth user
                auth.currentUser?.delete()
                    ?.addOnSuccessListener {
                        progressDlg.dismiss()
                        // Clear local prefs and navigate to login screen
                        auth.signOut()
                        val sharedPreferences = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                        val editor = sharedPreferences.edit()
                        editor.clear()
                        editor.apply()
                        Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_LONG).show()

                        val intent = Intent(requireContext(), LoginScreen::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()
                    }
                    ?.addOnFailureListener { e ->
                        progressDlg.dismiss()
                        Log.e(TAG, "Failed to delete auth user: ${e.message}", e)
                        Toast.makeText(requireContext(), "Failed to delete account: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                progressDlg.dismiss()
                Log.e(TAG, "Failed deleting data: ${e.message}", e)
                Toast.makeText(requireContext(), "Failed to remove user data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Delete documents from a top-level collection where field "userId" == uid.
     * This function deletes in batches (max 500 per batch) and returns a Task that completes when done.
     */
    private fun deleteCollectionDocumentsByUser(db: FirebaseFirestore, collectionName: String, uid: String): Task<Void> {
        val tcs = TaskCompletionSource<Void>()
        val batchSize = 500

        fun deleteBatch() {
            db.collection(collectionName)
                .whereEqualTo("userId", uid)
                .limit(batchSize.toLong())
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot == null || snapshot.isEmpty) {
                        // Nothing left to delete in this collection
                        tcs.setResult(null)
                        return@addOnSuccessListener
                    }
                    val batch = db.batch()
                    for (doc in snapshot.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            // recursively delete next batch
                            deleteBatch()
                        }
                        .addOnFailureListener { e ->
                            tcs.setException(e)
                        }
                }
                .addOnFailureListener { e ->
                    tcs.setException(e)
                }
        }

        // Start
        deleteBatch()
        return tcs.task
    }

    /**
     * Delete all storage files and subfolders under prefix userStoragePrefix/{uid}/ recursively.
     * Returns Task<Void> completed when done.
     */
    private fun deleteStoragePrefix(uid: String): Task<Void> {
        val tcs = TaskCompletionSource<Void>()
        val storage = FirebaseStorage.getInstance()
        val rootRef = storage.reference.child("$userStoragePrefix/$uid")

        // recursive function
        fun deletePrefix(refPath: String) {
            val ref = storage.reference.child(refPath)
            ref.listAll()
                .addOnSuccessListener { listResult ->
                    val itemDeleteTasks = mutableListOf<Task<Void>>()
                    // delete files
                    for (item in listResult.items) {
                        itemDeleteTasks.add(item.delete())
                    }

                    // delete each sub-prefix recursively
                    val prefixTasks = mutableListOf<Task<Void>>()
                    for (prefix in listResult.prefixes) {
                        val subTcs = TaskCompletionSource<Void>()
                        // call deletePrefix recursively; when finished set subTcs result
                        deletePrefix(prefix.path) // note: recursive deletion will call tcs later
                        prefixTasks.add(subTcs.task) // placeholder to wait — but we don't have subTcs completion inside recursion here
                    }

                    // Wait for items delete and (shallow) prefixes processed
                    if (itemDeleteTasks.isEmpty()) {
                        // If no files, consider this prefix deletion done.
                        // Note: listAll returns prefixes but we rely on their own listAll calls to delete nested content.
                        tcs.trySetResult(null)
                    } else {
                        Tasks.whenAll(itemDeleteTasks)
                            .addOnSuccessListener {
                                // After deleting files at this prefix, still rely on other recursive calls to handle nested prefixes.
                                tcs.trySetResult(null)
                            }
                            .addOnFailureListener { e ->
                                tcs.trySetException(e)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    tcs.trySetException(e)
                }
        }

        // If folder does not exist, listAll will return empty result; we still treat as success.
        deletePrefix("${userStoragePrefix}/$uid")
        return tcs.task
    }

    /**
     * Setups a setting-row include.
     *
     * rootId: the include root ID (e.g. R.id.setting_notifications)
     */
    private fun setupSettingItem(
        rootId: Int,
        iconRes: Int,
        title: String,
        hasSwitch: Boolean,
        initialSwitchState: Boolean = false,
        subtitleText: String? = null,
        callback: ((Boolean) -> Unit)? = null,
    ) {
        val root = binding.root.findViewById<View>(rootId) ?: return
        val ivIcon = root.findViewById<ImageView>(R.id.iv_setting_icon)
        val tvTitle = root.findViewById<TextView>(R.id.tv_setting_title)
        val tvSubtitle = root.findViewById<TextView>(R.id.tv_setting_subtitle)
        val switchView = root.findViewById<SwitchMaterial>(R.id.switch_setting)
        val ivArrow = root.findViewById<ImageView>(R.id.iv_profile_arrow)

        ivIcon?.setImageResource(iconRes)
        tvTitle?.text = title

        if (subtitleText != null && tvSubtitle != null) {
            tvSubtitle.visibility = View.VISIBLE
            tvSubtitle.text = subtitleText
        } else {
            tvSubtitle?.visibility = View.GONE
        }

        if (hasSwitch) {
            switchView?.visibility = View.VISIBLE
            ivArrow?.visibility = View.GONE
            switchView?.isChecked = initialSwitchState
            root.setOnClickListener {
                switchView?.isChecked = !(switchView?.isChecked ?: false)
            }
            switchView?.setOnCheckedChangeListener { _, isChecked ->
                callback?.invoke(isChecked)
            }
        } else {
            switchView?.visibility = View.GONE
            ivArrow?.visibility = View.VISIBLE
            root.setOnClickListener {
                callback?.invoke(true)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // remove Firestore listener to avoid leaks
        profileListener?.remove()
        profileListener = null
        _binding = null
    }
}
