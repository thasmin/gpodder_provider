package com.axelby.gpodder;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBAdapter extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "gpodder.db";
	private static final int DATABASE_VERSION = 2;

	public DBAdapter(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE subscriptions(url TEXT PRIMARY KEY);");
		db.execSQL("CREATE TABLE pending_add(url TEXT PRIMARY KEY NOT NULL);");
		db.execSQL("CREATE TABLE pending_remove(url TEXT PRIMARY KEY NOT NULL);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (newVersion < 2) {
			// cleared to clean out sync errors
			clearDB(db);
		}
	}

	private void clearDB(SQLiteDatabase db) {
		db.delete("subscriptions", null, null);
		db.delete("pending_add", null, null);
		db.delete("pending_remove", null, null);
	}

}
