package com.example.donatedrop.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.donatedrop.R
import com.example.donatedrop.models.User

class UserAdapter(
    private var items: MutableList<User>,
    private val onItemClick: (user: User) -> Unit,
    private val onMenuClick: (view: View, user: User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserVH>() {

    fun updateList(newList: List<User>) {
        items = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun removeUserById(uid: String) {
        val index = items.indexOfFirst { it.id == uid }
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_users, parent, false)
        return UserVH(v)
    }

    override fun onBindViewHolder(holder: UserVH, position: Int) {
        val user = items[position]
        holder.bind(user)
        holder.itemView.setOnClickListener { onItemClick(user) }
        holder.ivMenu.setOnClickListener { onMenuClick(it, user) }
    }

    override fun getItemCount(): Int = items.size

    class UserVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val roleChip: TextView = itemView.findViewById(R.id.roleChip)
        val ivMenu: ImageView = itemView.findViewById(R.id.ivMenu)


        fun bind(user: User) {
            tvName.text = user.name
            tvEmail.text = user.email
            roleChip.text = user.role

            // style role chip color depending on role
            if (user.role.equals("Admin", ignoreCase = true)) {
                roleChip.setBackgroundResource(R.drawable.badge_background)
            } else {
                // different background if needed (optional)
                roleChip.setBackgroundResource(R.drawable.badge_background)
            }

            if (!user.avatarUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(user.avatarUrl)
                    .circleCrop()
                    .into(ivAvatar)
            } else {
                Glide.with(itemView.context)
                    .load(R.drawable.user)
                    .circleCrop()
                    .into(ivAvatar)
            }

        }
    }
}
