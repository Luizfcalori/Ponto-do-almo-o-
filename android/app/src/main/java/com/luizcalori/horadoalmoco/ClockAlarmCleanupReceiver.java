package com.luizcalori.horadoalmoco;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.AlarmClock;

public class ClockAlarmCleanupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String label = intent == null ? null : intent.getStringExtra("alarm_label");
        if (label == null || label.isBlank() || Build.VERSION.SDK_INT < 23) return;

        try {
            Intent dismissIntent = new Intent(AlarmClock.ACTION_DISMISS_ALARM);
            dismissIntent.putExtra(
                    AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                    AlarmClock.ALARM_SEARCH_MODE_LABEL
            );
            dismissIntent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
            dismissIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (dismissIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(dismissIntent);
            }
        } catch (Exception ignored) {}

        android.content.SharedPreferences prefs =
                context.getSharedPreferences("controle_ponto_alarms", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor edit = prefs.edit();

        if (label.equals(prefs.getString("clock_alarm_before_label", null))) {
            edit.remove("clock_alarm_before_label");
        }
        if (label.equals(prefs.getString("clock_alarm_return_label", null))) {
            edit.remove("clock_alarm_return_label");
        }
        edit.apply();
    }
}
