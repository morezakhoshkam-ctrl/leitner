package com.example.leitner

import android.app.*
import android.content.*
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result { Notif.show(applicationContext); return Result.success() }
}

class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", 0)
        Store.answer(c, id, i.getBooleanExtra("knew", false))
        c.getSystemService(NotificationManager::class.java).cancel(nid(id))
    }
}

fun nid(id: Long) = (id % 1000000).toInt()

object Notif {
    fun show(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("lt", "کلمات روزانه", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        Store.due(c).take(5).forEach { w ->
            fun act(knew: Boolean) = PendingIntent.getBroadcast(
                c, nid(w.id) * 2 + (if (knew) 1 else 0),
                Intent(c, AnswerReceiver::class.java).putExtra("id", w.id).putExtra("knew", knew),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(c, "lt")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(w.word)
                .setContentText(w.meaning)
                .setStyle(Notification.BigTextStyle().bigText(w.meaning))
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(null, "بلد بودم ✅", act(true)).build())
                .addAction(Notification.Action.Builder(null, "بلد نبودم ❌", act(false)).build())
                .build()
            nm.notify(nid(w.id), n)
        }
    }

    // هر روز در ساعت انتخاب‌شده
    fun schedule(c: Context, hour: Int) {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(t.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
