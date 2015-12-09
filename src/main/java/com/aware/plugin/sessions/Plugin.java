package com.aware.plugin.sessions;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.aware.Applications;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.Battery;
import com.aware.Communication;
import com.aware.ESM;
import com.aware.Screen;
import com.aware.plugin.sessions.Provider.Session_Data;
import com.aware.providers.Applications_Provider;
import com.aware.providers.Battery_Provider;
import com.aware.providers.ESM_Provider;
import com.aware.providers.Screen_Provider.Screen_Data;
import com.aware.utils.Aware_Plugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;




public class Plugin extends Aware_Plugin {
    private SharedPreferences prefs;

    public static JSONArray esm_queue;
    //private variables that hold the latest values to be shared whenever ACTION_AWARE_CURRENT_CONTEXT is broadcasted
    private static double elapsed_device_off;
    private static double elapsed_device_on;
    private static String screen_state;
    private static String foreground_application;
    private static String foreground_package;
    private static int esm_status;
    private static String esm_user_answer;
    private static ContextProducer sContext;
    private static String charge;
    private static String discharge;
    private static String full;
    private static String low;
    private static String shutdown;
    private static String reboot;
    private static String session;
    private static boolean unlocked;
    private static boolean screenOn;
    public static AlertDialog alert;
    public static LinearLayout answersHolder;
    public static Button answer;
    public static Button answer2;

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        TAG = "SESSIONS";

        //need global variable to determine session type
        unlocked = true;
        screenOn = true;


        // Activate any sensors/plugins needed - not needed when running as plugin in study
//        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_SCREEN, true);
//        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ESM, true);
//        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS, true);
//        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY, true);

        IntentFilter screen_filter = new IntentFilter();
        screen_filter.addAction(Screen.ACTION_AWARE_SCREEN_ON);
        screen_filter.addAction(Screen.ACTION_AWARE_SCREEN_OFF);
        screen_filter.addAction(Screen.ACTION_AWARE_SCREEN_UNLOCKED);

        IntentFilter application_filter = new IntentFilter();
        application_filter.addAction(Applications.ACTION_AWARE_APPLICATIONS_FOREGROUND);
        application_filter.addAction(Applications.ACTION_AWARE_APPLICATIONS_NOTIFICATIONS);

        IntentFilter battery_filter = new IntentFilter();
        battery_filter.addAction(Battery.ACTION_AWARE_BATTERY_CHARGING);
        battery_filter.addAction(Battery.ACTION_AWARE_BATTERY_DISCHARGING);
        battery_filter.addAction(Battery.ACTION_AWARE_BATTERY_FULL);
        battery_filter.addAction(Battery.ACTION_AWARE_BATTERY_LOW);
        battery_filter.addAction(Battery.ACTION_AWARE_PHONE_REBOOT);
        battery_filter.addAction(Battery.ACTION_AWARE_PHONE_SHUTDOWN);

        IntentFilter esm_filter = new IntentFilter();
        esm_filter.addAction(ESM.ACTION_AWARE_ESM_ANSWERED);
        esm_filter.addAction(ESM.ACTION_AWARE_ESM_DISMISSED);
        esm_filter.addAction(ESM.ACTION_AWARE_ESM_EXPIRED);

        // Listen if there is already an ESM send
        IntentFilter esm_queue_fired_filter = new IntentFilter();
        esm_queue_fired_filter.addAction(ESM.ACTION_AWARE_ESM_QUEUE_STARTED);
        IntentFilter esm_queue_completed_filter = new IntentFilter();
        esm_queue_completed_filter.addAction(ESM.ACTION_AWARE_ESM_QUEUE_COMPLETE);

        IntentFilter communication_filter = new IntentFilter();
        communication_filter.addAction(Communication.ACTION_AWARE_CALL_RINGING);
        communication_filter.addAction(Communication.ACTION_AWARE_MESSAGE_RECEIVED);

        registerReceiver(screenListener, screen_filter);
        registerReceiver(applicationListener, application_filter);
        registerReceiver(batteryListener, battery_filter);
        registerReceiver(esmStatusListener, esm_filter);
        registerReceiver(esmFiredListener, esm_queue_fired_filter);
        registerReceiver(esmCompletedListener, esm_queue_completed_filter);

        //Shares this plugin's context to AWARE and applications
        sContext = new ContextProducer() {
            @Override
            public void onContext() {
                ContentValues context_data = new ContentValues();
                context_data.put(Session_Data.TIMESTAMP, System.currentTimeMillis());
                context_data.put(Session_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                context_data.put(Session_Data.SCREEN_STATE, screen_state);
                context_data.put(Session_Data.APPLICATION, foreground_application);
                context_data.put(Session_Data.PACKAGE, foreground_package);
                context_data.put(Session_Data.ESM_STATUS, esm_status);
                context_data.put(Session_Data.ESM_USER_ANSWER, esm_user_answer);
                context_data.put(Session_Data.ELAPSED_DEVICE_OFF, elapsed_device_off);
                context_data.put(Session_Data.ELAPSED_DEVICE_ON, elapsed_device_on);
                context_data.put(Session_Data.CHARGE, charge);
                context_data.put(Session_Data.DISCHARGE, discharge);
                context_data.put(Session_Data.BATTERY_LOW, low);
                context_data.put(Session_Data.BATTERY_FULL, full);
                context_data.put(Session_Data.BATTERY_SHUTDOWN, shutdown);
                context_data.put(Session_Data.BATTERY_REBOOT, reboot);
                context_data.put(Session_Data.SESSION, session);
                if (DEBUG) Log.d(TAG, context_data.toString());

                //insert data to table
                getContentResolver().insert(Session_Data.CONTENT_URI, context_data);
            }
        };

        CONTEXT_PRODUCER = sContext;

        // To sync data to the server, you'll need to set this variables from your ContentProvider
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Provider.Session_Data.CONTENT_URI};

        IntentFilter boot_filter = new IntentFilter("session_boot_new");
        boot_filter.addAction("session_boot_old");
        registerReceiver(bootListener, boot_filter);


        if (Aware.getSetting(this, "study_id").length() == 0) {
            Intent joinStudy = new Intent(this, Aware_Preferences.StudyConfig.class);
            joinStudy.putExtra(Aware_Preferences.StudyConfig.EXTRA_JOIN_STUDY, "https://api.awareframework.com/index.php/webservice/index/410/u6Es5y8OW48a");
            startService(joinStudy);
        }
        startBootESM();
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));

    }

    private void startBootESM() {


        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Why did you start your phone?");
        builder.setMessage("Please choose below.");

        final View layout = inflater.inflate(R.layout.question, null);
        builder.setView(layout);
        answersHolder = (LinearLayout) layout.findViewById(R.id.esm_boot_question);
        answer = new Button(this);
        answer2 = new Button(this);
        alert = builder.create();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f);
        answer.setLayoutParams(params);
        answer.setText("Start to use my phone.");
        answer.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendBroadcast(new Intent("session_boot_new"));
                alert.dismiss();

            }
        });
        answer2.setLayoutParams(params);
        answer2.setText("Continue to use my phone.");
        answer2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendBroadcast(new Intent("session_boot_old"));
                alert.dismiss();

            }
        });
        answersHolder.addView(answer);
        answersHolder.addView(answer2);
        alert.setCanceledOnTouchOutside(false);

        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        alert.show();
        alert.getWindow().getAttributes();
        View v = (View)alert.getWindow().findViewById(android.R.id.message).getParent();
        v.setMinimumHeight(0);
        TextView textView = (TextView) alert.findViewById(android.R.id.message);
        textView.setTextSize(13);
        textView.setMinimumHeight(0);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(screenListener != null) { unregisterReceiver(screenListener); }
        if(applicationListener != null) { unregisterReceiver(applicationListener); }
        if(esmStatusListener != null) { unregisterReceiver(esmStatusListener); }
        if(batteryListener != null) { unregisterReceiver(batteryListener); }
        if(esmFiredListener != null) { unregisterReceiver(esmFiredListener); }
        if(esmCompletedListener != null) { unregisterReceiver(esmCompletedListener); }
        if(bootListener!= null) { unregisterReceiver(bootListener); }

        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_SCREEN, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ESM, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_APPLICATIONS, false);
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY, false);

        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    public static JSONObject getSessionAnswer() {
        JSONObject jsonObject = new JSONObject();
        try {
            JSONObject q1Body = new JSONObject();
            q1Body.put("esm_type", ESM.TYPE_ESM_QUICK_ANSWERS);
            q1Body.put("esm_title", "Why did you unlock your phone?");
            q1Body.put("esm_instructions", "Please choose below.");
            q1Body.put("esm_quick_answers", new JSONArray().put("Start to use my phone.").put("Continue to use my phone."));
            q1Body.put("esm_submit", "Next");
            q1Body.put("esm_expiration_threshold", 300);
            q1Body.put("esm_trigger", "trigger");
            jsonObject.put("esm", q1Body);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }



    public static void variableReset() {
        elapsed_device_off = 0;
        elapsed_device_on = 0;
        screen_state = "";
        foreground_application = "";
        foreground_package = "";
        esm_status = -1;
        esm_user_answer = "";
        charge = "";
        discharge = "";
        full = "";
        low = "";
        shutdown = "";
        reboot = "";
        session = "";
    }

    /**
     * BroadcastReceiver that will receiver screen events from AWARE
     */
    private static ScreenListener screenListener = new ScreenListener();

    public static class ScreenListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            variableReset();
            final Context mycontext = context;
            if (intent.getAction().equals(Screen.ACTION_AWARE_SCREEN_ON)) {
                Log.d("SESSION", "SCREEN ON");
                screenOn = true;

                if (unlocked) { //if the phone is unlocked, must be user control
                    session = "3";
                }
                if (!unlocked) {
                    //something turns screen on
                    //charging, discharging, foreground app shortly before this, not user control, it is notification
                    //session="2";

                    Cursor cursor0 = context.getContentResolver().query(Battery_Provider.Battery_Discharges.CONTENT_URI, null, null, null, Battery_Provider.Battery_Discharges.TIMESTAMP + " DESC LIMIT 1");
                    if (cursor0 != null && cursor0.moveToFirst()) {
                        double timeOfDischarging = System.currentTimeMillis() - cursor0.getDouble(cursor0.getColumnIndex(Battery_Provider.Battery_Discharges.TIMESTAMP));
                        if (timeOfDischarging < 2000) {
                            session = "2";
                        }
                    }
                    if (cursor0 != null && !cursor0.isClosed()) cursor0.close();

                    Cursor cursor1 = context.getContentResolver().query(Battery_Provider.Battery_Charges.CONTENT_URI, null, null, null, Battery_Provider.Battery_Charges.TIMESTAMP + " DESC LIMIT 1");
                    if (cursor1 != null && cursor1.moveToFirst()) {
                        double timeOfCharging = System.currentTimeMillis() - cursor1.getDouble(cursor1.getColumnIndex(Battery_Provider.Battery_Charges.TIMESTAMP));
                        if (timeOfCharging < 2000) {
                            session = "2";
                        }
                    }
                    if (cursor1 != null && !cursor1.isClosed()) cursor1.close();

                    Cursor cursor = context.getContentResolver().query(Applications_Provider.Applications_Foreground.CONTENT_URI, null, null, null, Applications_Provider.Applications_Foreground.TIMESTAMP + " DESC LIMIT 1");
                    if (cursor != null && cursor.moveToFirst()) {
                        double timeOfForegroundAppBeforeScreenOn = System.currentTimeMillis() - cursor.getDouble(cursor.getColumnIndex(Applications_Provider.Applications_Foreground.TIMESTAMP));
                        if (timeOfForegroundAppBeforeScreenOn < 2000) {
                            session = "2";
                        }
                    }
                    if (cursor != null && !cursor.isClosed()) cursor.close();

                    //no charging, discharging, foreground app shortly before this, must be user control

                    //session="1";
                    if (!session.equals("2")) {
                        session = "1";
                    }
                }
                //Start timer on
                elapsed_device_on = 0;
                screen_state = "on";

                //Query screen data for when was the last time the screen was off
                Cursor last_time_off = context.getContentResolver().query(Screen_Data.CONTENT_URI, null, Screen_Data.SCREEN_STATUS + " = " + Screen.STATUS_SCREEN_OFF, null, Screen_Data.TIMESTAMP + " DESC LIMIT 1");
                if (last_time_off != null && last_time_off.moveToFirst()) {
                    //Calculate how long has it been until now that the screen was off
                    elapsed_device_off = System.currentTimeMillis() - last_time_off.getDouble(last_time_off.getColumnIndex(Screen_Data.TIMESTAMP));
                }
                if (last_time_off != null && !last_time_off.isClosed()) last_time_off.close();
            }

            if (intent.getAction().equals(Screen.ACTION_AWARE_SCREEN_UNLOCKED)) {
                Log.d("SESSION", "SCREEN UNLOCKED");

                screen_state = "unlocked";
                session = "3";
                unlocked = true;



                alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                alert.show();
                alert.getWindow().getAttributes();
                View v = (View)alert.getWindow().findViewById(android.R.id.message).getParent();
                v.setMinimumHeight(0);
                TextView textView = (TextView) alert.findViewById(android.R.id.message);
                textView.setTextSize(13);
                textView.setMinimumHeight(0);

            }

            if (intent.getAction().equals(Screen.ACTION_AWARE_SCREEN_OFF)) {
                //Start timer off
                elapsed_device_off = 0;
                screen_state = "off";
                unlocked = false;
                screenOn = false;
                //Query screen data for when was the last time the screen was on
                Cursor last_time_on = context.getContentResolver().query(Screen_Data.CONTENT_URI, null, Screen_Data.SCREEN_STATUS + " = " + Screen.STATUS_SCREEN_ON, null, Screen_Data.TIMESTAMP + " DESC LIMIT 1");
                if (last_time_on != null && last_time_on.moveToFirst()) {
                    //Calculate how long has it been until now that the screen was on
                    elapsed_device_on = System.currentTimeMillis() - last_time_on.getDouble(last_time_on.getColumnIndex(Screen_Data.TIMESTAMP));
                }
                if (last_time_on != null && !last_time_on.isClosed()) last_time_on.close();
            }
            //Share context
            sContext.onContext();
        }
    }

    private static ESMFiredListener esmFiredListener = new ESMFiredListener();

    public static class ESMFiredListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // ESM queue started
            //ESMOngoing = true;
        }
    }
    private static ESMCompletedListener esmCompletedListener = new ESMCompletedListener();

    public static class ESMCompletedListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // ESM queue completed
            //ESMOngoing = false;
        }
    }

    /**
     * BroadcastReceiver that will receiver ESM events from AWARE
     */
    private static ESMStatusListener esmStatusListener = new ESMStatusListener();

    public static class ESMStatusListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            variableReset();
            if (unlocked) {
                session = "3";
            }
            Cursor cursor = context.getContentResolver().query(ESM_Provider.ESM_Data.CONTENT_URI, null, null, null, ESM_Provider.ESM_Data.TIMESTAMP + " DESC LIMIT 1");

            if (cursor != null && cursor.moveToFirst()) {
                esm_status = cursor.getInt(cursor.getColumnIndex(ESM_Provider.ESM_Data.STATUS));
                esm_user_answer = cursor.getString(cursor.getColumnIndex(ESM_Provider.ESM_Data.ANSWER));
                Log.d("SESSION", "User answer " + esm_user_answer);
            }
            if (cursor != null && !cursor.isClosed()) cursor.close();

            sContext.onContext();
        }
    }

    /**
     * BroadcastReceiver that will receive application events from AWARE
     */
    private static ApplicationListener applicationListener = new ApplicationListener();

    public static class ApplicationListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            variableReset();

            if (unlocked) { //unlocked session
                session = "3";
            }

            if ((!unlocked) && (screenOn)) { //locked session, user uses app when locked
                session = "1";
            }
            if (intent.getAction().equals(Applications.ACTION_AWARE_APPLICATIONS_FOREGROUND)) {
                Cursor cursor = context.getContentResolver().query(Applications_Provider.Applications_Foreground.CONTENT_URI, null, null, null, Applications_Provider.Applications_Foreground.TIMESTAMP + " DESC LIMIT 1");
                if (cursor != null && cursor.moveToFirst()) {
                    foreground_application = cursor.getString(cursor.getColumnIndex(Applications_Provider.Applications_Foreground.APPLICATION_NAME));
                    foreground_package = cursor.getString(cursor.getColumnIndex(Applications_Provider.Applications_Foreground.PACKAGE_NAME));
                    Log.d("Session foreground", foreground_package);
                }
                if (cursor != null && !cursor.isClosed()) cursor.close();
            }
            if (intent.getAction().equals(Applications.ACTION_AWARE_APPLICATIONS_NOTIFICATIONS)) {
                Log.d("Session notification", "");
                Cursor cursor = context.getContentResolver().query(Applications_Provider.Applications_Notifications.CONTENT_URI, null, null, null, Applications_Provider.Applications_Notifications.TIMESTAMP + " DESC LIMIT 1");
                if (cursor != null && cursor.moveToFirst()) {
                    //String notificationName = cursor.getString(cursor.getColumnIndex(Applications_Provider.Applications_Notifications.APPLICATION_NAME));
                    String notification_package = cursor.getString(cursor.getColumnIndex(Applications_Provider.Applications_Notifications.PACKAGE_NAME));
                    Log.d("Session notification", notification_package);
                }
                if (cursor != null && !cursor.isClosed()) cursor.close();
            }
            //Share context
            sContext.onContext();
        }
    }

    /**
     * BroadcastReceiver that will receiver battery events from AWARE
     */
    private static BatteryListener batteryListener = new BatteryListener();

    public static class BatteryListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            variableReset();

            if (intent.getAction().equals(Battery.ACTION_AWARE_BATTERY_CHARGING)) {
                //Start timer off
                charge = "yes";
                if (unlocked) {
                    session = "3";
                }
                Log.d("Session", "charge");
            }
            if (intent.getAction().equals(Battery.ACTION_AWARE_BATTERY_DISCHARGING)) {
                //Start timer off
                discharge = "yes";
                if (unlocked) {
                    session = "3";
                }
                Log.d("Session", "discharge");
            }
            if (intent.getAction().equals(Battery.ACTION_AWARE_BATTERY_FULL)) {
                //Start timer off
                full = "yes";
                if (unlocked) {
                    session = "3";
                }
            }
            if (intent.getAction().equals(Battery.ACTION_AWARE_BATTERY_LOW)) {
                //Start timer off
                low = "yes";
                if (unlocked) {
                    session = "3";
                }
            }
            if (intent.getAction().equals(Battery.ACTION_AWARE_PHONE_SHUTDOWN)) {
                //Start timer off
                shutdown = "yes";
                if (unlocked) {
                    session = "3";
                }
                unlocked = false;
                Log.d("Session", "shutdown");
            }
            if (intent.getAction().equals(Battery.ACTION_AWARE_PHONE_REBOOT)) {
                //Start timer off
                reboot = "yes";
                if (unlocked) {
                    session = "3";
                }
                unlocked = false;
                Log.d("Session", "reboot");
            }
            //Share context
            sContext.onContext();
        }
    }

    private static BootListener bootListener = new BootListener();

    public static class BootListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            variableReset();
            if (intent.getAction().equals("session_boot_new")) {
                // ESM new objective answer
                esm_status = 2;
                esm_user_answer = "Start to use my phone.";
                Log.d("Session", "new");
                session = "3";
            }
            if (intent.getAction().equals("session_boot_old")) {
                // ESM old objective answer
                //
                esm_status = 2;
                esm_user_answer = "Continue to use my phone.";
                Log.d("Session", "old");
                session = "3";
            }
            //Share context
            sContext.onContext();
        }
    }
}
