package com.axelby.gpodder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Vector;

import javax.net.ssl.HttpsURLConnection;

import net.iHarder.Base64;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class Client {

	private static class Config {
		public String mygpo = "https://gpodder.net/";
		public String mygpo_feedservice = "https://mygpo-feedservice.appspot.com/";
		public long update_timeout = 604800L;
	}
	
	private static Config _config;
	private static Calendar _configRefresh = null;

	static {
		verifyCurrentConfig();
	}

	public static void verifyCurrentConfig() {
		if (_configRefresh == null || _configRefresh.before(new GregorianCalendar())) {
			_config = retrieveGPodderConfig();

			// do NOT use basic auth over HTTP without SSL
			if (_config.mygpo.startsWith("http://"))
				_config.mygpo = "https://" + _config.mygpo.substring(7);
			if (_config.mygpo_feedservice.startsWith("http://"))
				_config.mygpo_feedservice = "https://" + _config.mygpo_feedservice.substring(7);

			_configRefresh = new GregorianCalendar();
			_configRefresh.add(Calendar.MILLISECOND, (int) _config.update_timeout);
		}
	}

	private static Config retrieveGPodderConfig() {
		Config config = new Config();

		try {
			URL url = new URL("http://gpodder.net/clientconfig.json");
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.addRequestProperty("User-Agent", "GPodder.net Account for Android");
			JsonReader reader = new JsonReader(new InputStreamReader(conn.getInputStream()));
			reader.beginObject();
			
			// get mygpo
			reader.nextName(); // should be mygpo
			reader.beginObject();
			reader.nextName(); // should be baseurl
			config.mygpo = reader.nextString();
			reader.endObject();

			// get mygpo-feedservice
			reader.nextName(); // should be mygpo-feedservice
			reader.beginObject();
			reader.nextName(); // should be baseurl
			config.mygpo_feedservice = reader.nextString();
			reader.endObject();

			// get update_timeout
			reader.nextName();
			config.update_timeout = reader.nextLong();

			reader.endObject();
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return config;
	}

	private Context _context;
	private String _username;
	private String _password;
	private String _sessionId;

	public Client(Context context, String username, String password) {
		_context = context;
		_username = username;
		_password = password;
	}

	private void writePost(HttpsURLConnection conn, String toPost)
			throws IOException {
		conn.setDoOutput(true);
		OutputStream output = null;
		try {
		     output = conn.getOutputStream();
		     output.write(toPost.getBytes());
		} finally {
		     if (output != null) try { output.close(); } catch (IOException logOrIgnore) {}
		}
	}

	public HttpsURLConnection createConnection(URL url) throws IOException, Exception {
		HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();

		if (_sessionId == null) {
			// basic authentication
			String toBase64 = _username + ":" + _password;
			conn.addRequestProperty("Authorization", "basic " + new String(Base64.encodeBytes(toBase64.getBytes())));
		} else {
			conn.addRequestProperty("Cookie", "sessionid=" + _sessionId);
		}
		conn.addRequestProperty("User-Agent", "GPodder.net Account for Android");

		return conn;
	}

	public boolean authenticate() {
		verifyCurrentConfig();

		URL url;
		HttpsURLConnection conn = null;
		try {
			url = new URL(_config.mygpo + "api/2/auth/" + _username + "/login.json");
			conn = createConnection(url);
			writePost(conn, " ");

			conn.connect();

			int code = conn.getResponseCode();
			if (code != 200)
				return false;

			for (String val : conn.getHeaderFields().get("Set-Cookie")) {
				String[] data = val.split(";")[0].split("=");
				if (data[0].equals("sessionid"))
					_sessionId = data[1];
			}

			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (conn != null)
				conn.disconnect();
		}
		return true;
	}

	public class Changes {
		public Vector<String> added = new Vector<String>();
		public Vector<String> removed = new Vector<String>();
		public int timestamp = 0;
		public boolean isValid() { return timestamp != 0; }
		public boolean isEmpty() { return added.size() > 0 || removed.size() > 0; }
	}

	public Changes getSubscriptionChanges(int lastCheck) {
		verifyCurrentConfig();

		URL url;
		HttpsURLConnection conn = null;
		Changes changes = new Changes();
		try {
			url = new URL(_config.mygpo + "api/2/subscriptions/" + _username + "/podax.json?since=" + String.valueOf(lastCheck));
			conn = createConnection(url);

			conn.connect();

			int code = conn.getResponseCode();
			if (code != 200)
				return changes;

			InputStream stream = conn.getInputStream();
			JsonReader reader = new JsonReader(new InputStreamReader(stream));
			reader.beginObject();

			// get add
			while (reader.hasNext()) {
				String key = reader.nextName();
				if (key.equals("timestamp")) {
					changes.timestamp = reader.nextInt();
				} else if (key.equals("add")) {
					reader.beginArray();
					while (reader.hasNext())
						changes.added.add(reader.nextString());
					reader.endArray();
				} else if (key.equals("remove")) {
					reader.beginArray();
					while (reader.hasNext())
						changes.removed.add(reader.nextString());
					reader.endArray();
				}
			}

			reader.endObject();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (conn != null)
				conn.disconnect();
		}

		return changes;
	}

	public void syncDiffs() {
		verifyCurrentConfig();

		SQLiteDatabase db = new DBAdapter(_context).getWritableDatabase();

		Vector<String> toAdd = new Vector<String>();
		Cursor c = db.rawQuery("SELECT url FROM pending_add", null);
		while (c.moveToNext())
			toAdd.add(c.getString(0));
		c.close();

		Vector<String> toRemove = new Vector<String>();
		c = db.rawQuery("SELECT url FROM pending_remove", null);
		while (c.moveToNext())
			toRemove.add(c.getString(0));
		c.close();

		if (toAdd.size() == 0 && toRemove.size() == 0) {
			db.close();
			return;
		}

		URL url;
		HttpsURLConnection conn = null;
		try {
			url = new URL(_config.mygpo + "api/2/subscriptions/" + _username + "/podax.json");
			conn = createConnection(url);

			conn.setDoOutput(true);
			OutputStreamWriter streamWriter = new OutputStreamWriter(conn.getOutputStream());
			JsonWriter writer = new JsonWriter(streamWriter);
			writer.beginObject();

			// rectify removed urls by adding them
			writer.name("add");
			writer.beginArray();
			for (String s : toAdd)
				writer.value(s);
			writer.endArray();

			// rectify added urls by removing them
			writer.name("remove");
			writer.beginArray();
			for (String s : toRemove)
				writer.value(s);
			writer.endArray();

			writer.endObject();
			streamWriter.close();

			conn.connect();

			int code = conn.getResponseCode();
			if (code != 200)
				return;

			// clear out the pending tables
			db.execSQL("DELETE FROM pending_add");
			db.execSQL("DELETE FROM pending_remove");

			// no need to handle the output
			Log.d("gpodder", "done syncing");

			db.close();

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
