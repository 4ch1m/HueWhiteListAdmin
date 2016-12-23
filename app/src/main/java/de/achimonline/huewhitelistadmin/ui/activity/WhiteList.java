package de.achimonline.huewhitelistadmin.ui.activity;

import android.app.Activity;
import android.os.Bundle;
import de.achimonline.huewhitelistadmin.R;

public class WhiteList extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.whitelist_title));

        setContentView(R.layout.whitelist);
    }
}
