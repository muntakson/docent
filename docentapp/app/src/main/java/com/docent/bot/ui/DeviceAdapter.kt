package com.docent.bot.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.docent.bot.databinding.ItemDeviceBinding
import com.docent.bot.model.ProjectorDevice

class DeviceAdapter(
    private val onDeviceClick: (ProjectorDevice) -> Unit
) : ListAdapter<ProjectorDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: ProjectorDevice) {
            binding.tvDeviceName.text = device.displayName
            binding.tvDeviceInfo.text = "${device.host}:${device.port} - ${device.type}"
            binding.ivSelected.visibility = if (device.isSelected) View.VISIBLE else View.GONE

            binding.cardDevice.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<ProjectorDevice>() {
        override fun areItemsTheSame(oldItem: ProjectorDevice, newItem: ProjectorDevice): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProjectorDevice, newItem: ProjectorDevice): Boolean {
            return oldItem == newItem
        }
    }
}
