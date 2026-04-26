package com.namaazalarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.namaazalarm.model.DailyPrayers
import com.namaazalarm.model.PrayerName
import com.namaazalarm.model.PrayerTime
import com.namaazalarm.util.TimeFormatter
import java.util.Calendar

class AlarmPreviewAdapter(
    private var days: MutableList<DailyPrayers>,
    private val use24Hour: Boolean,
    private val onEdit: (DailyPrayers, PrayerName, PrayerTime) -> Unit
) : RecyclerView.Adapter<AlarmPreviewAdapter.DayViewHolder>() {

    private val today = Calendar.getInstance().let {
        Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_prayers, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) = holder.bind(days[position])
    override fun getItemCount() = days.size

    fun updateDays(newDays: List<DailyPrayers>) {
        days.clear(); days.addAll(newDays); notifyDataSetChanged()
    }

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDay:     TextView = itemView.findViewById(R.id.tvDay)
        private val tvFajr:    TextView = itemView.findViewById(R.id.tvFajr)
        private val tvZuhr:    TextView = itemView.findViewById(R.id.tvZuhr)
        private val tvAsr:     TextView = itemView.findViewById(R.id.tvAsr)
        private val tvMaghrib: TextView = itemView.findViewById(R.id.tvMaghrib)
        private val tvIsha:    TextView = itemView.findViewById(R.id.tvIsha)

        fun bind(day: DailyPrayers) {
            tvDay.text = "${day.day}  ${day.getDayOfWeekName()}"

            fun wire(tv: TextView, prayer: PrayerName) {
                val t = day.prayers[prayer]
                tv.text  = t?.let { TimeFormatter.format(it.hour, it.minute, use24Hour) } ?: "--:--"
                tv.alpha = if (t != null) 1f else 0.4f
                tv.setOnClickListener { if (t != null) onEdit(day, prayer, t) }
            }

            wire(tvFajr, PrayerName.FAJR); wire(tvZuhr, PrayerName.ZUHR)
            wire(tvAsr, PrayerName.ASR);   wire(tvMaghrib, PrayerName.MAGHRIB)
            wire(tvIsha, PrayerName.ISHA)

            val isToday = day.year == today.first && day.month == today.second && day.day == today.third
            if (isToday) itemView.setBackgroundResource(R.drawable.bg_today_highlight)
            else itemView.setBackgroundColor(
                ContextCompat.getColor(itemView.context, R.color.card_bg)
            )
        }
    }
}
