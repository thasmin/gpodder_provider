package com.axelby.gpodder;

import android.app.ListActivity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.SimpleCursorAdapter;

public class SubscriptionsActivity extends ListActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Cursor cursor = getContentResolver().query(Provider.URI, null, null, null, "url");
		String[] from = new String[] { "url" };
		int[] to = new int[] { android.R.id.text1 };
		setListAdapter(new Adapter(this, android.R.layout.simple_list_item_1, cursor, from, to));
	}

	public class Adapter extends SimpleCursorAdapter {

		@Override
		public boolean areAllItemsEnabled() {
			return false;
		}

		@Override
		public boolean isEnabled(int position) {
			return false;
		}

		public Adapter(Context context, int layout, Cursor c, String[] from, int[] to) {
			super(context, layout, c, from, to);
		}
		
	}

}
