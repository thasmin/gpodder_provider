package com.axelby.gpodder;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public class Provider extends ContentProvider {
	public static String AUTHORITY = "com.axelby.gpodder.podcasts";
	public static Uri URI = Uri.parse("content://" + AUTHORITY);
	public static final String ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
			+ "/vnd.axelby.gpodder.podcast";
	public static final String DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
			+ "/vnd.axelby.gpodder.podcast";

	public static final String URL = "url";

	@Override
	public boolean onCreate() {
		return true;
	}
	
	// if uri is the authority, all podcasts will be return
	// otherwise, only match will be returned
	@Override
	public String getType(Uri uri) {
		return uri.equals(AUTHORITY) ? DIR_TYPE : ITEM_TYPE;
	}

	// if uri is the authority, all podcasts will be return
	// otherwise, only match will be returned
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		DBAdapter dbAdapter = new DBAdapter(this.getContext());
		SQLiteDatabase db = dbAdapter.getReadableDatabase();
		if (uri.equals(AUTHORITY))
			return db.rawQuery("SELECT url FROM " +
					"(SELECT url FROM subscriptions UNION select url from pending_add)" +
					"WHERE url NOT IN (SELECT url FROM pending_remove)", null);
		else
			return db.query("subscriptions", new String[] { "url" }, "url = ?", new String[] { uri.getPath() }, null, null, null);
	}

	// not a valid operation
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		return 0;
	}

	// remove it from the pending_remove table
	// if it's not in the subscriptions table, add it to the pending_add table
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		if (!uri.equals(AUTHORITY) || !values.containsKey(URL))
			return null;
		String url = values.getAsString(URL);
		DBAdapter dbAdapter = new DBAdapter(this.getContext());
		SQLiteDatabase db = dbAdapter.getReadableDatabase();
		db.delete("pending_remove", "url = ?", new String[] { url });
		Cursor c = db.rawQuery("SELECT COUNT(*) FROM subscriptions WHERE url = ?", new String[] { url });
		c.moveToFirst();
		if (c.getLong(0) == 0)
			db.insert("pending_add", null, values);
		return Uri.withAppendedPath(URI, url);
	}

	// remove it from the pending_add table
	// if it's in the subscriptions table, add it to the pending_remove table
	// selection is ignored, selection args[0] is matched
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		if (!uri.equals(AUTHORITY) || selectionArgs.length == 0)
			return 0;
		String url = selectionArgs[0];
		DBAdapter dbAdapter = new DBAdapter(this.getContext());
		SQLiteDatabase db = dbAdapter.getReadableDatabase();
		db.delete("pending_add", "url = ?", new String[] { url });
		Cursor c = db.rawQuery("SELECT COUNT(*) FROM subscriptions WHERE url = ?", new String[] { url });
		c.moveToFirst();
		if (c.getLong(0) == 1) {
			ContentValues values = new ContentValues();
			values.put(URL, url);
			db.insert("pending_add", null, values);
		}
		return 1;
	}

}
