package de.achimonline.huewhitelistadmin.ui.fragment;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.philips.lighting.hue.listener.PHBridgeConfigurationListener;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.model.PHBridgeConfiguration;
import com.philips.lighting.model.PHHueError;
import de.achimonline.huewhitelistadmin.R;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class WhiteListItemDetail extends Fragment implements OnClickListener
{
    public static final String EXTRA_USER_NAME = "USER_NAME";
    public static final String EXTRA_APP_NAME = "APP_NAME";
    public static final String EXTRA_DEVICE_NAME = "DEVICE_NAME";

    private String userName;
    private String appName;
    private String deviceName;

    private TextView createdAtTextView;
    private TextView lastUsedAtTextView;

    private final PHHueSDK phHueSDK = PHHueSDK.create();

    public static WhiteListItemDetail newInstance(int index, String userName, String appName, String deviceName)
    {
        final WhiteListItemDetail whiteListItemDetail = new WhiteListItemDetail();

        final Bundle args = new Bundle();
        args.putInt(WhiteList.EXTRA_INDEX, index);
        args.putString(EXTRA_USER_NAME, userName);
        args.putString(EXTRA_APP_NAME, appName);
        args.putString(EXTRA_DEVICE_NAME, deviceName);

        whiteListItemDetail.setArguments(args);

        return whiteListItemDetail;
    }

    public int getShownIndex()
    {
        return getArguments().getInt(WhiteList.EXTRA_INDEX, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        if (container == null)
        {
            return null;
        }

        userName = getArguments().getString(EXTRA_USER_NAME);
        appName = getArguments().getString(EXTRA_APP_NAME);
        deviceName = getArguments().getString(EXTRA_DEVICE_NAME);

        // ---

        final View view = inflater.inflate(R.layout.whitelistitemdetail, container, false);

        final TextView userNameTextView = (TextView) view.findViewById(R.id.whitelistitemdetail_username);
        final TextView appNameLabelTextView = (TextView) view.findViewById(R.id.whitelistitemdetail_appname_label);
        final TextView appNameTextView = (TextView) view.findViewById(R.id.whitelistitemdetail_appname);
        final TextView deviceNameLabelTextView = (TextView) view.findViewById(R.id.whitelistitemdetail_devicename_label);
        final TextView deviceNameTextView = (TextView) view.findViewById(R.id.whitelistitemdetail_devicename);

        createdAtTextView = (TextView) view.findViewById(R.id.whitelistitemdetail_createdat);
        lastUsedAtTextView = (TextView) view.findViewById(R.id.whitelistitemdetail_lastusedat);

        final TextView ownUsernameWarningTextView = (TextView) view.findViewById(R.id.whitelistitemdetail_own_username_info);
        final Button removeButton = (Button) view.findViewById(R.id.whitelistitemdetail_remove_button);

        // ---

        userNameTextView.setText(userName);
        appNameTextView.setText(appName);
        deviceNameTextView.setText(deviceName);

        if (appName == null || "".equals(appName))
        {
            appNameLabelTextView.setVisibility(View.GONE);
            appNameTextView.setVisibility(View.GONE);
        }

        if (deviceName == null || "".equals(deviceName))
        {
            deviceNameLabelTextView.setVisibility(View.GONE);
            deviceNameTextView.setVisibility(View.GONE);
        }

        // check if it's the username of our own app
        final boolean ownUsername = phHueSDK.getSelectedBridge().getResourceCache().getBridgeConfiguration().getUsername().equals(userName);

        ownUsernameWarningTextView.setText(String.format(getString(R.string.whitelistitemdetail_own_username_info), getString(R.string.app_name)));
        ownUsernameWarningTextView.setVisibility(ownUsername ? View.VISIBLE : View.GONE);

        removeButton.setOnClickListener(this);
        removeButton.setVisibility(ownUsername ? View.GONE : View.VISIBLE);

        // the current HUE-API doesn't offer the "created at" and/or "last used at" date for a whitelist-entry;
        // so we'll have to do a separate query to the bridge and parse the JSON-data ourselves
        new GetDateValuesTask().execute(phHueSDK.getSelectedBridge().getResourceCache().getBridgeConfiguration().getIpAddress(), userName);

        return view;
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.whitelistitemdetail_remove_button:
                final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(getString(R.string.whitelistitemdetail_removal_confirmation));
                builder.setPositiveButton(getString(android.R.string.yes), new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id)
                    {
                        removeWhiteListEntry();
                    }
                });
                builder.setNegativeButton(getString(android.R.string.no), null);
                builder.setCancelable(false);
                builder.create().show();
                break;
        }
    }

    private void removeWhiteListEntry()
    {
        phHueSDK.getSelectedBridge().removeUsername(userName, new PHBridgeConfigurationListener()
        {
            @Override
            public void onReceivingConfiguration(PHBridgeConfiguration phBridgeConfiguration)
            {
            }

            @Override
            public void onSuccess()
            {
                getActivity().runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        final Toast toast = Toast.makeText(getActivity(), getString(R.string.whitelistitemdetail_toast_removed), Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
                        toast.show();
                    }
                });

                getActivity().finish();

                // if we've been in dualpane-mode, immediately "reload" the activity
                if ((getActivity() instanceof de.achimonline.huewhitelistadmin.ui.activity.WhiteList))
                {
                    getActivity().startActivity(getActivity().getIntent());
                }
            }

            @Override
            public void onError(int i, String s)
            {
            }

            @Override
            public void onStateUpdate(Map<String, String> map, List<PHHueError> list)
            {
            }
        });
    }

    private class GetDateValuesTask extends AsyncTask<String, Void, String>
    {
        private String ipAddress;
        private String userName;

        private static final String API_URL = "http://%s/api/%s/config";

        private static final String JSON_WHITELIST = "whitelist";
        private static final String JSON_CREATE_DATE = "create date";
        private static final String JSON_LAST_USE_DATE = "last use date";

        @Override
        protected String doInBackground(String... params)
        {
            String jsonString = null;

            if (params != null && params.length == 2)
            {
                ipAddress = params[0];
                userName = params[1];

                jsonString = getBridgeConfig();
            }

            return jsonString;
        }

        @Override
        protected void onPostExecute(String jsonString)
        {
            if (jsonString != null)
            {
                try
                {
                    final JSONObject config = new JSONObject(jsonString);
                    final JSONObject whitelistEntry = config.getJSONObject(JSON_WHITELIST).getJSONObject(userName);

                    final String createDate = parseDateString(whitelistEntry.getString(JSON_CREATE_DATE));
                    final String lastUseDate = parseDateString(whitelistEntry.getString(JSON_LAST_USE_DATE));

                    createdAtTextView.setText(createDate);
                    lastUsedAtTextView.setText(lastUseDate);
                }
                catch (Exception e)
                {
                }
            }
        }

        private String getBridgeConfig()
        {
            final DefaultHttpClient httpClient = new DefaultHttpClient();

            try
            {
                final HttpGet getRequest = new HttpGet(String.format(API_URL, ipAddress, userName));
                getRequest.addHeader("accept", "application/json");

                final HttpResponse response = httpClient.execute(getRequest);

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
                {
                    return IOUtils.toString(response.getEntity().getContent(), "UTF-8");
                }
            }
            catch (Exception e)
            {
            }
            finally
            {
                httpClient.getConnectionManager().shutdown();
            }

            return null;
        }

        private String parseDateString(String dateString) throws ParseException
        {
            final Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(dateString);

            final Calendar calendar = GregorianCalendar.getInstance();
            calendar.setTime(date);
            calendar.add(Calendar.MILLISECOND, TimeZone.getDefault().getRawOffset());

            if (TimeZone.getDefault().inDaylightTime(new Date()))
            {
                calendar.add(Calendar.HOUR, 1);
            }

            return DateFormat.getDateTimeInstance().format(calendar.getTime());
        }
    }
}
