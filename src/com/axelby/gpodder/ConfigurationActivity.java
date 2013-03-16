package com.axelby.gpodder;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

public class ConfigurationActivity extends Activity {

	private Account _account;
	private Client _client;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.configuration);

		final Context context = this;

		_account = (Account) getIntent().getExtras().get("account");
		String username = _account.name;
		AccountManager accountManager = AccountManager.get(context);
		String password = accountManager.getPassword(_account);
		_client = new Client(context, username, password);

		final EditText deviceNameEdit = (EditText)findViewById(R.id.device_name);
		final ProgressDialog loadingDialog = ProgressDialog.show(context, "Loading...", "Retrieving configuration from GPodder.net");

		findViewById(R.id.save).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				String deviceName = deviceNameEdit.getText().toString();
				final ProgressDialog savingDialog = ProgressDialog.show(context, "Saving...", "Saving to GPodder.net");
				new AsyncTask<String, Void, Boolean>() {
					@Override
					protected Boolean doInBackground(String... params) {
						return _client.setDeviceName(params[0]);
					}

					@Override
					protected void onPostExecute(Boolean result) {
						savingDialog.dismiss();
						finish();
					}
				}.execute(deviceName);
			}
		});

		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				return _client.getDeviceName();
			}

			@Override
			protected void onPostExecute(String result) {
			    if (result != null) {
			        deviceNameEdit.append(result);
			    }
				loadingDialog.dismiss();
			}
		}.execute();
	}

}
