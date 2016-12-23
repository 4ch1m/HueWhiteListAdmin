package de.achimonline.huewhitelistadmin.ui.fragment;

import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.*;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.hue.sdk.heartbeat.PHHeartbeatManager;
import com.philips.lighting.model.PHWhiteListEntry;
import de.achimonline.huewhitelistadmin.R;

import java.util.ArrayList;
import java.util.List;

public class WhiteList extends ListFragment
{
    private PHHueSDK phHueSDK;
    private PHHeartbeatManager phHeartbeatManager;

    private ArrayList<PHWhiteListEntry> whiteListEntries;

    private boolean dualPaneMode;

    private int currentPosition = 0;

    public static final String EXTRA_INDEX = "INDEX";
    public static final String INSTANCESTATE_CURRENTCHOICE = "CURRENTCHOICE";

    private static final int WHITELISTITEMDETAIL_RESULTCODE = 0;

    private GetWhiteListEntriesTask getWhiteListEntriesTask;

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        phHueSDK = PHHueSDK.create();
        phHeartbeatManager = PHHeartbeatManager.getInstance();

        whiteListEntries = new ArrayList<PHWhiteListEntry>();

        setListAdapter(new WhiteListEntryListAdapter(getActivity(), whiteListEntries));
        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        setEmptyText(getString(R.string.whitelist_empty));

        setHasOptionsMenu(true);

        final View detailsFrame = getActivity().findViewById(R.id.details);

        dualPaneMode = detailsFrame != null && detailsFrame.getVisibility() == View.VISIBLE;

        if (savedInstanceState != null)
        {
            currentPosition = savedInstanceState.getInt(INSTANCESTATE_CURRENTCHOICE, 0);
        }

        if (dualPaneMode)
        {
            showDetails(currentPosition);
        }

        loadWhiteListEntries();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        loadWhiteListEntries();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putInt(INSTANCESTATE_CURRENTCHOICE, currentPosition);
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id)
    {
        showDetails(position);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.whitelist, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.refresh:
                loadWhiteListEntries();
                break;
        }

        return true;
    }

    private void showDetails(int index)
    {
        currentPosition = index;

        if (whiteListEntries.size() > 0)
        {
            final String userName = whiteListEntries.get(index).getUserName();
            final String appName = whiteListEntries.get(index).getAppName();
            final String deviceName = whiteListEntries.get(index).getDeviceName();

            if (dualPaneMode)
            {
                getListView().setItemChecked(index, true);

                WhiteListItemDetail details = (WhiteListItemDetail) getFragmentManager().findFragmentById(R.id.details);

                if (details == null || details.getShownIndex() != index)
                {
                    details = WhiteListItemDetail.newInstance(index, userName, appName, deviceName);

                    final FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                    fragmentTransaction.replace(R.id.details, details);
                    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                    fragmentTransaction.commit();
                }
            }
            else
            {
                final Intent intent = new Intent();
                intent.setClass(getActivity(), de.achimonline.huewhitelistadmin.ui.activity.WhiteListItemDetail.class);
                intent.putExtra(EXTRA_INDEX, index);
                intent.putExtra(WhiteListItemDetail.EXTRA_USER_NAME, userName);
                intent.putExtra(WhiteListItemDetail.EXTRA_APP_NAME, appName);
                intent.putExtra(WhiteListItemDetail.EXTRA_DEVICE_NAME, deviceName);

                startActivityForResult(intent, WHITELISTITEMDETAIL_RESULTCODE);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        loadWhiteListEntries();
    }

    private void loadWhiteListEntries()
    {
        if (getWhiteListEntriesTask == null || getWhiteListEntriesTask.getStatus() == AsyncTask.Status.FINISHED)
        {
            getWhiteListEntriesTask = new GetWhiteListEntriesTask();
            getWhiteListEntriesTask.execute();
        }
    }

    private static class WhiteListEntryListAdapter extends BaseAdapter
    {
        private LayoutInflater layoutInflater;
        private List<PHWhiteListEntry> whiteListEntries;

        private class WhiteListEntryItem
        {
            private TextView position;
            private TextView userName;
            private TextView appName;
            private TextView deviceName;
        }

        public WhiteListEntryListAdapter(Context context, List<PHWhiteListEntry> whiteListEntries)
        {
            layoutInflater = LayoutInflater.from(context);

            this.whiteListEntries = whiteListEntries;
        }

        @Override
        public int getCount()
        {
            return whiteListEntries.size();
        }

        @Override
        public Object getItem(int position)
        {
            return whiteListEntries.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            WhiteListEntryItem whiteListEntryItem;

            if (convertView == null)
            {
                convertView = layoutInflater.inflate(R.layout.whitelistitem, null);

                whiteListEntryItem = new WhiteListEntryItem();
                whiteListEntryItem.position = (TextView) convertView.findViewById(R.id.whitelistentryitem_position);
                whiteListEntryItem.userName = (TextView) convertView.findViewById(R.id.whitelistentryitem_username);
                whiteListEntryItem.appName = (TextView) convertView.findViewById(R.id.whitelistentryitem_appname);
                whiteListEntryItem.deviceName = (TextView) convertView.findViewById(R.id.whitelistentryitem_devicename);

                convertView.setTag(whiteListEntryItem);
            }
            else
            {
                whiteListEntryItem = (WhiteListEntryItem) convertView.getTag();
            }

            PHWhiteListEntry whiteListEntry = whiteListEntries.get(position);

            final String userName = whiteListEntry.getUserName();
            final String appName = whiteListEntry.getAppName();
            final String deviceName = whiteListEntry.getDeviceName();

            whiteListEntryItem.position.setText((position + 1) + ".");
            whiteListEntryItem.userName.setText(userName);
            whiteListEntryItem.appName.setText(appName);
            whiteListEntryItem.deviceName.setText(deviceName);

            whiteListEntryItem.appName.setVisibility(appName == null || "".equals(appName) ? View.GONE : View.VISIBLE);
            whiteListEntryItem.deviceName.setVisibility(deviceName == null || "".equals(deviceName) ? View.GONE : View.VISIBLE);

            return convertView;
        }
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();

        phHeartbeatManager.disableFullConfigHeartbeat(phHueSDK.getSelectedBridge());
    }

    // this is more or less an ugly workaround...
    // enabling the heartbeat when creating this activity didn't seem to keep it alive;
    // so we're enabling the heartbeat every time we want to fetch the current
    // whitelist-entries and wait until one heartbeat happened
    private class GetWhiteListEntriesTask extends AsyncTask<Void, Void, Void>
    {
        @Override
        protected void onPreExecute()
        {
            whiteListEntries.clear();

            ((WhiteListEntryListAdapter)getListAdapter()).notifyDataSetChanged();
        }

        @Override
        protected Void doInBackground(Void... voids)
        {
            phHeartbeatManager.enableFullConfigHeartbeat(phHueSDK.getSelectedBridge(), 1_000l);

            try
            {
                Thread.sleep(1_001l);
            }
            catch (InterruptedException e)
            {
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid)
        {
            whiteListEntries.addAll(phHueSDK.getSelectedBridge().getResourceCache().getBridgeConfiguration().getWhiteListEntries());

            ((WhiteListEntryListAdapter)getListAdapter()).notifyDataSetChanged();
        }
    }
}
