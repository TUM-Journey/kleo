package de.tum.ase.kleo.android.ui.fragment;

import android.support.annotation.LayoutRes;

import de.tum.ase.kleo.android.R;

public class WelcomeFragment extends BaseFragment {

    @Override
    @LayoutRes
    protected int provideLayoutResourceId() {
        return R.layout.fragment_welcome;
    }
}
