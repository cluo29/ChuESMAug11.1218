package com.aware.plugin.sessions;

/**
 * Created by niels on 13/07/15.
 */
import java.util.HashMap;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

public class Provider extends ContentProvider {
    public static final int DATABASE_VERSION = 13;

    public static String AUTHORITY = "com.aware.plugin.sessions.provider.sessions";

    private static final int sessions = 1;
    private static final int sessions_ID = 2;
    public static final String DATABASE_NAME = Environment.getExternalStorageDirectory() + "/AWARE/plugin_sessions.db";

    public static final String[] DATABASE_TABLES = {"plugin_sessions"};
    public static final class Session_Data implements BaseColumns {

        private Session_Data(){}

        public static final Uri CONTENT_URI = Uri.parse("content://"+AUTHORITY+"/plugin_sessions");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.sessions";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.sessions";

        public static final String _ID = "_id";
        public static final String DEVICE_ID = "device_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String SCREEN_STATE = "screen_state";

        public static final String ESM_STATUS = "esm_status";
        public static final String ESM_USER_ANSWER = "esm_user_answer";

        public static final String APPLICATION = "application";
        public static final String PACKAGE = "package";

        public static final String ELAPSED_DEVICE_OFF = "elapsed_device_off";
        public static final String ELAPSED_DEVICE_ON = "elapsed_device_on";

        public static final String CHARGE = "charge";
        public static final String DISCHARGE = "discharge";
        public static final String BATTERY_LOW = "battery_low";
        public static final String BATTERY_FULL = "battery_full";
        public static final String BATTERY_SHUTDOWN = "battery_shutdown";
        public static final String BATTERY_REBOOT = "battery_reboot";
        public static final String SESSION = "session";
    }


    public static final String[] TABLES_FIELDS = {
            Session_Data._ID + " integer primary key autoincrement," +
                    Session_Data.DEVICE_ID + " text default ''," +
                    Session_Data.TIMESTAMP + " real default 0," +
                    Session_Data.SCREEN_STATE + " text default ''," +
                    Session_Data.ESM_STATUS + " integer default 0," +
                    Session_Data.ESM_USER_ANSWER + " text default ''," +
                    Session_Data.APPLICATION + " text default ''," +
                    Session_Data.PACKAGE + " text default ''," +
                    Session_Data.ELAPSED_DEVICE_OFF + " integer default 0," +
                    Session_Data.ELAPSED_DEVICE_ON + " integer default 0," +
                    Session_Data.CHARGE + " text default ''," +
                    Session_Data.DISCHARGE + " text default ''," +
                    Session_Data.BATTERY_LOW + " text default ''," +
                    Session_Data.BATTERY_FULL + " text default ''," +
                    Session_Data.BATTERY_SHUTDOWN + " text default ''," +
                    Session_Data.BATTERY_REBOOT + " text default ''," +
                    Session_Data.SESSION + " text default ''," +
                    "UNIQUE("+ Session_Data.TIMESTAMP+","+ Session_Data.DEVICE_ID+")"
    };
    private static UriMatcher URIMatcher;
    private static HashMap<String, String> databaseMap;
    private static DatabaseHelper databaseHelper;
    private static SQLiteDatabase database;

    @Override
    public boolean onCreate() {
        URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], sessions);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0] + "/#", sessions_ID);

        databaseMap = new HashMap<>();
        databaseMap.put(Session_Data._ID, Session_Data._ID);
        databaseMap.put(Session_Data.DEVICE_ID, Session_Data.DEVICE_ID);
        databaseMap.put(Session_Data.TIMESTAMP, Session_Data.TIMESTAMP);
        databaseMap.put(Session_Data.SCREEN_STATE, Session_Data.SCREEN_STATE);
        databaseMap.put(Session_Data.ESM_STATUS, Session_Data.ESM_STATUS);
        databaseMap.put(Session_Data.ESM_USER_ANSWER, Session_Data.ESM_USER_ANSWER);
        databaseMap.put(Session_Data.APPLICATION, Session_Data.APPLICATION);
        databaseMap.put(Session_Data.PACKAGE, Session_Data.PACKAGE);
        databaseMap.put(Session_Data.ELAPSED_DEVICE_OFF, Session_Data.ELAPSED_DEVICE_OFF);
        databaseMap.put(Session_Data.ELAPSED_DEVICE_ON, Session_Data.ELAPSED_DEVICE_ON);
        databaseMap.put(Session_Data.CHARGE, Session_Data.CHARGE);
        databaseMap.put(Session_Data.DISCHARGE, Session_Data.DISCHARGE);
        databaseMap.put(Session_Data.BATTERY_LOW, Session_Data.BATTERY_LOW);
        databaseMap.put(Session_Data.BATTERY_FULL, Session_Data.BATTERY_FULL);
        databaseMap.put(Session_Data.BATTERY_SHUTDOWN, Session_Data.BATTERY_SHUTDOWN);
        databaseMap.put(Session_Data.BATTERY_REBOOT, Session_Data.BATTERY_REBOOT);
        databaseMap.put(Session_Data.SESSION, Session_Data.SESSION);
        return true;
    }

    private boolean initializeDB() {
        if (databaseHelper == null) {
            databaseHelper = new DatabaseHelper( getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS );
        }
        if( databaseHelper != null && ( database == null || ! database.isOpen() )) {
            database = databaseHelper.getWritableDatabase();
        }
        return( database != null && databaseHelper != null);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (URIMatcher.match(uri)) {
            case sessions:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (URIMatcher.match(uri)) {
            case sessions:
                return Session_Data.CONTENT_TYPE;
            case sessions_ID:
                return Session_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        if (!initializeDB()) {
            Log.w(AUTHORITY, "Database unavailable...");
            return null;
        }

        ContentValues values = (initialValues != null) ? new ContentValues(
                initialValues) : new ContentValues();

        switch (URIMatcher.match(uri)) {
            case sessions:
                long weather_id = database.insert(DATABASE_TABLES[0], Session_Data.DEVICE_ID, values);

                if (weather_id > 0) {
                    Uri new_uri = ContentUris.withAppendedId(
                            Session_Data.CONTENT_URI,
                            weather_id);
                    getContext().getContentResolver().notifyChange(new_uri,
                            null);
                    return new_uri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (URIMatcher.match(uri)) {
            case sessions:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(databaseMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());
            return null;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        if( ! initializeDB() ) {
            Log.w(AUTHORITY,"Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (URIMatcher.match(uri)) {
            case sessions:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            default:

                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}