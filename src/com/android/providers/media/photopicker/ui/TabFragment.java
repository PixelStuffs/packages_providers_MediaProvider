/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.providers.media.photopicker.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.providers.media.R;
import com.android.providers.media.photopicker.PhotoPickerActivity;
import com.android.providers.media.photopicker.data.UserIdManager;
import com.android.providers.media.photopicker.viewmodel.PickerViewModel;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * The base abstract Tab fragment
 */
public abstract class TabFragment extends Fragment {

    private static final String TAG =  "PhotoPickerTabFragment";

    protected PickerViewModel mPickerViewModel;
    protected ImageLoader mImageLoader;
    protected AutoFitRecyclerView mRecyclerView;

    private int mBottomBarSize;
    private ExtendedFloatingActionButton mProfileButton;
    private UserIdManager mUserIdManager;
    private boolean mHideProfileButton;

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        return inflater.inflate(R.layout.fragment_picker_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mImageLoader = new ImageLoader(getContext());
        mRecyclerView = view.findViewById(R.id.photo_list);
        mRecyclerView.setHasFixedSize(true);
        mPickerViewModel = new ViewModelProvider(requireActivity()).get(PickerViewModel.class);

        mProfileButton = view.findViewById(R.id.profile_button);
        mUserIdManager = mPickerViewModel.getUserIdManager();
        if (mUserIdManager.isMultiUserProfiles()) {
            mProfileButton.setVisibility(View.VISIBLE);
            setUpProfileButton();
        }

        final boolean canSelectMultiple = mPickerViewModel.canSelectMultiple();
        if (canSelectMultiple) {
            final Button addButton = view.findViewById(R.id.button_add);
            addButton.setOnClickListener(v -> {
                ((PhotoPickerActivity) getActivity()).setResultAndFinishSelf();
            });

            final Button viewSelectedButton = view.findViewById(R.id.button_view_selected);
            // Transition to PreviewFragment on clicking "View Selected".
            viewSelectedButton.setOnClickListener(v -> {
                PreviewFragment.show(getActivity().getSupportFragmentManager());
            });
            mBottomBarSize = (int) getResources().getDimension(R.dimen.picker_bottom_bar_size);

            mPickerViewModel.getSelectedItems().observe(this, selectedItemList -> {
                final View bottomBar = view.findViewById(R.id.picker_bottom_bar);
                final int size = selectedItemList.size();
                int dimen = 0;
                if (size == 0) {
                    bottomBar.setVisibility(View.GONE);
                } else {
                    bottomBar.setVisibility(View.VISIBLE);
                    addButton.setText(generateAddButtonString(getContext(), size));
                    dimen = getBottomGapForRecyclerView(mBottomBarSize);
                }
                mRecyclerView.setPadding(0, 0, 0, dimen);

                if (mUserIdManager.isMultiUserProfiles()) {
                    if (selectedItemList.size() > 0) {
                        mProfileButton.hide();
                    } else {
                        if (!mHideProfileButton) {
                            mProfileButton.show();
                        }
                    }
                }
            });
        }
    }

    private void setUpProfileButton() {
        // TODO(b/190727775): Update profile button values onResume(). also re-check cross-profile
        //  restrictions.
        updateProfileButtonContent(mUserIdManager.isManagedUserSelected());
        updateProfileButtonColor(/* isDisabled */ !mUserIdManager.isCrossProfileAllowed());

        mProfileButton.setOnClickListener(v -> onClickProfileButton(v));
    }

    private void onClickProfileButton(View v) {
        if (!mUserIdManager.isCrossProfileAllowed()) {
            onClickShowErrorDialog(v);
        } else {
            onClickChangeProfile();
        }
    }

    private void onClickShowErrorDialog(View v) {
        if (mUserIdManager.isBlockedByAdmin()) {
            //TODO(b/190727775): launch dialog
            Snackbar.make(v, "Blocked by your admin", Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (mUserIdManager.isWorkProfileOff()) {
            //TODO(b/190727775): launch dialog
            Snackbar.make(v, "Turn on work apps?", Snackbar.LENGTH_SHORT).show();
            return;
        }
        return;
    }

    private void onClickChangeProfile() {
        if (mUserIdManager.isManagedUserSelected()) {
            // TODO(b/190024747): Add caching for performance before switching data to and fro
            // work profile
            mUserIdManager.setPersonalAsCurrentUserProfile();

        } else {
            // TODO(b/190024747): Add caching for performance before switching data to and fro
            // work profile
            mUserIdManager.setManagedAsCurrentUserProfile();
        }

        updateProfileButtonContent(mUserIdManager.isManagedUserSelected());

        mPickerViewModel.updateItems();
        mPickerViewModel.updateCategories();
    }

    private void updateProfileButtonContent(boolean isManagedUserSelected) {
        final int iconResId;
        final int textResId;
        if (isManagedUserSelected) {
            iconResId = R.drawable.ic_personal_mode;
            textResId = R.string.picker_personal_profile;
        } else {
            iconResId = R.drawable.ic_work_outline;
            textResId = R.string.picker_work_profile;
        }
        mProfileButton.setIconResource(iconResId);
        mProfileButton.setText(textResId);
    }

    private void updateProfileButtonColor(boolean isDisabled) {
        final int textAndIconResId;
        final int backgroundTintResId;
        if (isDisabled) {
            textAndIconResId = R.color.picker_profile_disabled_button_content_color;
            backgroundTintResId = R.color.picker_profile_disabled_button_background_color;
        } else {
            textAndIconResId = R.color.picker_profile_button_content_color;
            backgroundTintResId = R.color.picker_profile_button_background_color;
        }
        mProfileButton.setTextColor(AppCompatResources.getColorStateList(getContext(),
                textAndIconResId));
        mProfileButton.setIconTintResource(textAndIconResId);
        mProfileButton.setBackgroundTintList(AppCompatResources.getColorStateList(getContext(),
                backgroundTintResId));
    }

    protected int getBottomGapForRecyclerView(int bottomBarSize) {
        return bottomBarSize;
    }

    protected void hideProfileButton(boolean hide) {
        if (hide) {
            mProfileButton.hide();
            mHideProfileButton = true;
        } else if (!hide && mUserIdManager.isMultiUserProfiles()
                && mPickerViewModel.getSelectedItems().getValue().size() == 0) {
            mProfileButton.show();
            mHideProfileButton = false;
        }
    }

    private static String generateAddButtonString(Context context, int size) {
        final String sizeString = NumberFormat.getInstance(Locale.getDefault()).format(size);
        final String template = context.getString(R.string.picker_add_button_multi_select);
        return TextUtils.expandTemplate(template, sizeString).toString();
    }
}