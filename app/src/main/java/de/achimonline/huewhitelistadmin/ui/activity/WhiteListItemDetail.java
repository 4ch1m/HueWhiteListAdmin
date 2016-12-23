package de.achimonline.huewhitelistadmin.ui.activity;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import de.achimonline.huewhitelistadmin.R;

public class WhiteListItemDetail extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.whitelistitemdetail_title));

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            finish();
            return;
        }

        if (savedInstanceState == null)
        {
            final de.achimonline.huewhitelistadmin.ui.fragment.WhiteListItemDetail detailFragment = new de.achimonline.huewhitelistadmin.ui.fragment.WhiteListItemDetail();
            detailFragment.setArguments(getIntent().getExtras());

            getFragmentManager().beginTransaction().add(android.R.id.content, detailFragment).commit();
        }
    }
}
