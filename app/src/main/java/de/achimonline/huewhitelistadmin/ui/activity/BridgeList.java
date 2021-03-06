package de.achimonline.huewhitelistadmin.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.philips.lighting.hue.sdk.*;

import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHHueError;
import com.philips.lighting.model.PHHueParsingError;
import de.achimonline.huewhitelistadmin.R;
import de.achimonline.huewhitelistadmin.data.HueSharedPreferences;

import java.util.List;

public class BridgeList extends ListActivity
{
    private PHHueSDK phHueSDK;
    private HueSharedPreferences hueSharedPreferences;
    private AccessPointListAdapter accessPointListAdapter;

    private boolean lastSearchWasIPScan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.bridgelist_title));

        setContentView(R.layout.bridgelist);

        phHueSDK = PHHueSDK.create();
        phHueSDK.setAppName(getString(R.string.app_name));
        phHueSDK.setDeviceName(android.os.Build.MODEL);
        phHueSDK.getNotificationManager().registerSDKListener(phsdkListener);

        accessPointListAdapter = new AccessPointListAdapter(getApplicationContext(), phHueSDK.getAccessPointsFound());

        setListAdapter(accessPointListAdapter);

        hueSharedPreferences = HueSharedPreferences.getInstance(getApplicationContext());

        final String lastIpAddress = hueSharedPreferences.getLastConnectedIPAddress();
        final String lastUsername = hueSharedPreferences.getUsername();

        if (lastIpAddress != null && !"".equals(lastIpAddress))
        {
            final PHAccessPoint phAccessPoint = new PHAccessPoint();
            phAccessPoint.setIpAddress(lastIpAddress);
            phAccessPoint.setUsername(lastUsername);

            if (!phHueSDK.isAccessPointConnected(phAccessPoint))
            {
                PHWizardAlertDialog.getInstance().showProgressDialog(R.string.connecting, this);

                phHueSDK.connect(phAccessPoint);
            }
            else
            {
                startBaseActivity();
            }
        }
        else
        {
            doBridgeSearch();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.bridgelist, menu);

        return super.onCreateOptionsMenu(menu);
    }

    private final PHSDKListener phsdkListener = new PHSDKListener()
    {
        @Override
        public void onAccessPointsFound(List<PHAccessPoint> accessPoint)
        {
            PHWizardAlertDialog.getInstance().closeProgressDialog();

            if (accessPoint != null && accessPoint.size() > 0)
            {
                phHueSDK.getAccessPointsFound().clear();
                phHueSDK.getAccessPointsFound().addAll(accessPoint);

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        accessPointListAdapter.updateData(phHueSDK.getAccessPointsFound());
                    }
                });
            }
        }

        @Override
        public void onCacheUpdated(List<Integer> list, PHBridge phBridge)
        {
        }

        @Override
        public void onBridgeConnected(PHBridge phBridge, String userName)
        {
            phHueSDK.setSelectedBridge(phBridge);
            phHueSDK.enableHeartbeat(phBridge, PHHueSDK.HB_INTERVAL);
            phHueSDK.getLastHeartbeat().put(phBridge.getResourceCache().getBridgeConfiguration().getIpAddress(), System.currentTimeMillis());

            hueSharedPreferences.setLastConnectedIPAddress(phBridge.getResourceCache().getBridgeConfiguration().getIpAddress());
            hueSharedPreferences.setUsername(userName);

            PHWizardAlertDialog.getInstance().closeProgressDialog();

            startBaseActivity();
        }

        @Override
        public void onAuthenticationRequired(PHAccessPoint accessPoint)
        {
            phHueSDK.startPushlinkAuthentication(accessPoint);

            startActivity(new Intent(BridgeList.this, PushLinkAuthentication.class));
        }

        @Override
        public void onConnectionResumed(PHBridge bridge)
        {
            if (BridgeList.this.isFinishing())
            {
                return;
            }

            phHueSDK.getLastHeartbeat().put(bridge.getResourceCache().getBridgeConfiguration().getIpAddress(), System.currentTimeMillis());

            for (int i = 0; i < phHueSDK.getDisconnectedAccessPoint().size(); i++)
            {
                if (phHueSDK.getDisconnectedAccessPoint().get(i).getIpAddress().equals(bridge.getResourceCache().getBridgeConfiguration().getIpAddress()))
                {
                    phHueSDK.getDisconnectedAccessPoint().remove(i);
                }
            }
        }

        @Override
        public void onConnectionLost(PHAccessPoint accessPoint)
        {
            if (!phHueSDK.getDisconnectedAccessPoint().contains(accessPoint))
            {
                phHueSDK.getDisconnectedAccessPoint().add(accessPoint);
            }
        }

        @Override
        public void onError(int code, final String message)
        {
            if (code == PHHueError.AUTHENTICATION_FAILED || code == 1158)
            {
                PHWizardAlertDialog.getInstance().closeProgressDialog();
            }
            else if (code == PHHueError.BRIDGE_NOT_RESPONDING)
            {
                PHWizardAlertDialog.getInstance().closeProgressDialog();

                BridgeList.this.runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        PHWizardAlertDialog.showErrorDialog(BridgeList.this, message);
                    }
                });

            }
            else if (code == PHMessageType.BRIDGE_NOT_FOUND)
            {
                if (!lastSearchWasIPScan) // perform an IP scan (backup mechanism) if UPNP and portal search fails
                {
                    phHueSDK = PHHueSDK.getInstance();

                    final PHBridgeSearchManager phBridgeSearchManager = (PHBridgeSearchManager) phHueSDK.getSDKService(PHHueSDK.SEARCH_BRIDGE);
                    phBridgeSearchManager.search(false, false, true);

                    lastSearchWasIPScan = true;
                }
                else
                {
                    PHWizardAlertDialog.getInstance().closeProgressDialog();
                    BridgeList.this.runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            PHWizardAlertDialog.showErrorDialog(BridgeList.this, message);
                        }
                    });
                }
            }
        }

        @Override
        public void onParsingErrors(java.util.List<PHHueParsingError> parsingErrorsList)
        {
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.find_new_bridge:
                doBridgeSearch();
                break;
        }

        return true;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        if (phsdkListener != null)
        {
            phHueSDK.getNotificationManager().unregisterSDKListener(phsdkListener);
        }

        phHueSDK.disableAllHeartbeat();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        final PHAccessPoint accessPoint = (PHAccessPoint) accessPointListAdapter.getItem(position);

        final PHBridge connectedBridge = phHueSDK.getSelectedBridge();

        if (connectedBridge != null)
        {
            if (connectedBridge.getResourceCache().getBridgeConfiguration().getIpAddress() != null)
            {
                phHueSDK.disableHeartbeat(connectedBridge);
                phHueSDK.disconnect(connectedBridge);
            }
        }

        PHWizardAlertDialog.getInstance().showProgressDialog(R.string.connecting, this);

        phHueSDK.connect(accessPoint);
    }

    private void doBridgeSearch()
    {
        PHWizardAlertDialog.getInstance().showProgressDialog(R.string.searching, BridgeList.this);

        ((PHBridgeSearchManager) phHueSDK.getSDKService(PHHueSDK.SEARCH_BRIDGE)).search(true, true);
    }

    // starting the main activity this way, prevents the PushLinkAuthentication-activity being shown when pressing the back button.
    private void startBaseActivity()
    {
        final Intent intent = new Intent(getApplicationContext(), WhiteList.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(intent);
    }

    private static final class PHWizardAlertDialog
    {
        private ProgressDialog progressDialog;

        private static PHWizardAlertDialog phWizardAlertDialog;

        private PHWizardAlertDialog()
        {
        }

        public static synchronized PHWizardAlertDialog getInstance()
        {
            if (phWizardAlertDialog == null)
            {
                phWizardAlertDialog = new PHWizardAlertDialog();
            }

            return phWizardAlertDialog;
        }

        public static void showErrorDialog(Context activityContext, String msg)
        {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
            builder.setTitle(R.string.error).setMessage(msg).setPositiveButton(android.R.string.ok, null);

            final AlertDialog alert = builder.create();
            alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

            if (!((Activity) activityContext).isFinishing())
            {
                alert.show();
            }
        }

        public void closeProgressDialog()
        {
            if (progressDialog != null)
            {
                progressDialog.dismiss();
                progressDialog = null;
            }
        }

        public void showProgressDialog(int resID, Context ctx)
        {
            progressDialog = ProgressDialog.show(ctx, null, ctx.getString(resID), true, true);
            progressDialog.setCancelable(false);
        }

        public static void showAuthenticationErrorDialog(final Activity activityContext, String msg, int btnNameResId)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
            builder.setTitle(R.string.error).setMessage(msg).setPositiveButton(btnNameResId, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    activityContext.finish();
                }
            });

            final AlertDialog alert = builder.create();
            alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            alert.show();
        }
    }

    private static class AccessPointListAdapter extends BaseAdapter
    {
        private LayoutInflater layoutInflater;
        private List<PHAccessPoint> accessPoints;

        class BridgeListItem
        {
            private TextView bridgeIp;
            private TextView bridgeMac;
        }

        public AccessPointListAdapter(Context context, List<PHAccessPoint> accessPoints)
        {
            layoutInflater = LayoutInflater.from(context);

            this.accessPoints = accessPoints;
        }

        public View getView(final int position, View convertView, ViewGroup parent)
        {
            BridgeListItem item;

            if (convertView == null)
            {
                convertView = layoutInflater.inflate(R.layout.bridgeitem, null);

                item = new BridgeListItem();
                item.bridgeMac = (TextView) convertView.findViewById(R.id.bridge_mac);
                item.bridgeIp = (TextView) convertView.findViewById(R.id.bridge_ip);

                convertView.setTag(item);
            }
            else
            {
                item = (BridgeListItem) convertView.getTag();
            }

            final PHAccessPoint accessPoint = accessPoints.get(position);

            item.bridgeIp.setText(accessPoint.getIpAddress());
            item.bridgeMac.setText(accessPoint.getMacAddress());

            return convertView;
        }

        @Override
        public long getItemId(int position)
        {
            return 0;
        }

        @Override
        public int getCount()
        {
            return accessPoints.size();
        }

        @Override
        public Object getItem(int position)
        {
            return accessPoints.get(position);
        }

        public void updateData(List<PHAccessPoint> accessPoints)
        {
            this.accessPoints = accessPoints;

            notifyDataSetChanged();
        }
    }
}
