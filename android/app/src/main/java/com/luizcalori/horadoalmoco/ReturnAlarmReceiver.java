package com.luizcalori.horadoalmoco;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

public class ReturnAlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "lunch_return_alarm_v1";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        int notificationId = intent.getIntExtra("notification_id", 7302);

        if (title == null || title.isBlank()) title = "Controle de Ponto";
        if (message == null || message.isBlank()) message = "Hora de bater o ponto de retorno do almoço.";

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Lembretes do retorno do almoço",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Avisos 3 minutos antes e no horário de retorno do almoço.");
        channel.enableVibration(true);
        channel.setSound(alarmSound, audioAttributes);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);

        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());

        PendingIntent contentIntent = null;
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            contentIntent = PendingIntent.getActivity(
                    context,
                    notificationId + 100,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(message)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(2L * 60L * 1000L);

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent);
        }

        manager.notify(notificationId, builder.build());
    }
}
