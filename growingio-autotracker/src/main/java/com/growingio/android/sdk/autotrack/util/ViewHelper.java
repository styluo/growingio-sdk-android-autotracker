/*
 * Copyright (C) 2020 Beijing Yishu Technology Co., Ltd.
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

package com.growingio.android.sdk.autotrack.util;

import android.app.Activity;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;

import com.growingio.android.sdk.autotrack.IgnorePolicy;
import com.growingio.android.sdk.autotrack.events.ViewElement;
import com.growingio.android.sdk.autotrack.events.ViewElementEvent;
import com.growingio.android.sdk.autotrack.events.base.BaseViewElement;
import com.growingio.android.sdk.autotrack.models.ViewNode;
import com.growingio.android.sdk.autotrack.page.Page;
import com.growingio.android.sdk.autotrack.page.PageProvider;
import com.growingio.android.sdk.track.GrowingTracker;
import com.growingio.android.sdk.track.TrackMainThread;
import com.growingio.android.sdk.track.events.EventType;
import com.growingio.android.sdk.track.events.base.BaseEvent;
import com.growingio.android.sdk.track.providers.ActivityStateProvider;
import com.growingio.android.sdk.track.utils.ActivityUtil;
import com.growingio.android.sdk.track.utils.ClassExistHelper;
import com.growingio.android.sdk.track.utils.LinkedString;
import com.growingio.android.sdk.track.utils.LogUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class ViewHelper {

    private final static String TAG = "GIO.ViewHelper";

    private ViewHelper() {
    }

    public static boolean isViewSelfVisible(View mView) {
        if (mView == null || mView.getWindowVisibility() == View.GONE) {
            return false;
        }

        // home键back后, DecorView的visibility是 INVISIBLE, 即onResume时Window并不可见, 对GIO而言此时是可见的
        if (WindowHelper.isDecorView(mView)) {
            return true;
        }

        if (!(mView.getWidth() > 0
                && mView.getHeight() > 0
                && mView.getAlpha() > 0
                && mView.getLocalVisibleRect(new Rect()))) {
            return false;
        }

        //动画导致用户可见但是仍然 invisible,
        if (mView.getVisibility() != View.VISIBLE
                && mView.getAnimation() != null
                && mView.getAnimation().getFillAfter()) {
            return true;
        } else {
            return mView.getVisibility() == View.VISIBLE;
        }

    }

    public static boolean viewVisibilityInParents(View view) {
        if (view == null) {
            return false;
        }

        if (!isViewSelfVisible(view)) {
            return false;
        }

        ViewParent viewParent = view.getParent();
        while (viewParent instanceof View) {
            if (isViewSelfVisible((View) viewParent)) {
                viewParent = viewParent.getParent();
                if (viewParent == null) {
                    LogUtil.d(TAG, "Hit detached view: ", viewParent);
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public static ViewNode getClickViewNode(MenuItem menuItem) {
        if (menuItem == null) {
            return null;
        }
        WindowHelper.init();
        View[] windows = WindowHelper.getWindowViews();
        try {
            for (View window : windows) {
                if (window.getClass() == WindowHelper.sPopupWindowClazz) {
                    View menuView = findMenuItemView(window, menuItem);
                    if (menuView != null) {
                        return getClickViewNode(menuView);
                    }
                }
            }
            for (View window : windows) {
                if (window.getClass() != WindowHelper.sPopupWindowClazz) {
                    View menuView = findMenuItemView(window, menuItem);
                    if (menuView != null) {
                        return getClickViewNode(menuView);
                    }
                }
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static ViewNode getClickViewNode(View view) {
        if (view == null) {
            return null;
        }
        Activity activity = ActivityStateProvider.get().getForegroundActivity();
        if (activity == null || ViewHelper.isIgnoredView(view)) {
            return null;
        }

        ViewNode viewNode = getViewNode(view);

        if (viewNode == null) {
            return null;
        }

        return viewNode;
    }

    public static ViewElementEvent.EventBuilder getClickActionEvent(ViewNode viewNode) {
        if (viewNode == null || viewNode.view == null) {
            return null;
        }

        Activity activity = ActivityStateProvider.get().getForegroundActivity();
        if (activity == null || ViewHelper.isIgnoredView(viewNode.view)) {
            return null;
        }

        ViewElementEvent.EventBuilder event = new ViewElementEvent.EventBuilder();
        event.setEventType(EventType.CLICK);
        Page<?> page = PageProvider.get().findPage(viewNode.view);
        event.addElementBuilders(viewNodeToElementBuilders(viewNode))
                .setPageName(page.path())
                .setPageShowTimestamp(page.getShowTimestamp());

        return event;
    }

    public static ViewNode getTopViewNode(View view, List<View> viewTreeList) {
        if (viewTreeList == null) {
            viewTreeList = new ArrayList<View>(8);
        }

        ViewParent parent = view.getParent();
        viewTreeList.add(view);
        /*
         * view:
         * "AppCompactButton[2]#login_btn
         * parents:
         * ["LinearLayout[3]#login_container" ,"RelativeLayout[1]", ,"FrameLayout[0]#content", "PhoneWindow$DecorView"]
         */
        Page<?> page = ViewAttributeUtil.getViewPage(view);
        boolean needTraverse = (page == null || page.isIgnored()) && ViewAttributeUtil.getCustomId(view) == null;
        if (needTraverse) {
            while (parent instanceof ViewGroup) {
                if (ViewHelper.isIgnoredView((View) parent)) {
                    return null;
                }

                viewTreeList.add((ViewGroup) parent);
                if (ViewAttributeUtil.getCustomId((View) parent) != null) {
                    break;
                }
                page = ViewAttributeUtil.getViewPage((View) parent);
                if (page != null && !page.isIgnored()) {
                    break;
                }
                parent = parent.getParent();
            }
        }

        int endIndex = viewTreeList.size() - 1;
        View rootView = viewTreeList.get(endIndex);
        WindowHelper.init();

        boolean mParentIdSettled = false;
        String prefix = WindowHelper.getWindowPrefix(rootView);
        String opx = prefix;
        String px = prefix;

        if (!WindowHelper.isDecorView(rootView) && !(rootView.getParent() instanceof View)) {
            opx += "/" + Util.getSimpleClassName(rootView.getClass());
            px = opx;
            String id = Util.getIdName(rootView, mParentIdSettled);
            if (id != null) {
                if (ViewAttributeUtil.getViewId(rootView) != null) {
                    mParentIdSettled = true;
                }
                opx += "#" + id;
                px += "#" + id;
            }
        } else if (ViewAttributeUtil.getCustomId(rootView) != null) {
            opx = "/" + ViewAttributeUtil.getCustomId(rootView);
            px = opx;
        }

        return ViewNode.ViewNodeBuilder.newViewNode()
                .setView(rootView)
                .setLastListPos(-1)
                .setHasListParent(false)
                .setFullScreen(prefix.equals(WindowHelper.getMainWindowPrefix()))
                .setInClickableGroup(false)
                .setParentIdSettled(mParentIdSettled)
                .setOriginalParentXpath(LinkedString.fromString(opx))
                .setParentXPath(LinkedString.fromString(px))
                .setWindowPrefix(prefix)
                .setViewContent(Util.getViewContent(view, null))
                .setClickableParentXPath(LinkedString.fromString(px))
                .build();
    }

    public static ViewNode getViewNode(View view) {
        ArrayList<View> viewTreeList = new ArrayList<View>(8);

        ViewNode viewNode = getTopViewNode(view, viewTreeList);
        int endIndex = viewTreeList.size() - 1;

        for (int i = endIndex - 1; i >= 0; i--) {
            viewNode = viewNode.appendNode(viewTreeList.get(i));
        }

        return viewNode;
    }

    public static int getChildAdapterPositionInRecyclerView(View childView, ViewGroup parentView) {
        if (ClassExistHelper.instanceOfAndroidXRecyclerView(parentView)) {
            return ((androidx.recyclerview.widget.RecyclerView) parentView).getChildAdapterPosition(childView);
        } else if (ClassExistHelper.instanceOfSupportRecyclerView(parentView)) {
            // For low version RecyclerView
            try {
                return ((RecyclerView) parentView).getChildAdapterPosition(childView);
            } catch (Throwable e) {
                return ((RecyclerView) parentView).getChildPosition(childView);
            }

        } else if (ClassExistHelper.sHasCustomRecyclerView) {
            return ClassExistHelper.invokeCRVGetChildAdapterPositionMethod(parentView, childView);
        }
        return -1;
    }

    public static void persistClickEvent(BaseEvent.BaseEventBuilder<?> click) {
        TrackMainThread.trackMain().postEventToTrackMain(click);
    }

    private static View findMenuItemView(View view, MenuItem item) throws InvocationTargetException, IllegalAccessException {
        if (WindowHelper.getMenuItemData(view) == item) {
            return view;
        } else if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View menuView = findMenuItemView(((ViewGroup) view).getChildAt(i), item);
                if (menuView != null) {
                    return menuView;
                }
            }
        }
        return null;
    }

    private static boolean shouldChangeOn(View view, ViewNode viewNode) {
        if (view instanceof EditText) {
            String tag = ViewAttributeUtil.getMonitoringFocusKey(view);
            String lastText = tag == null ? "" : tag;
            String nowText = ((EditText) view).getText().toString();
            if ((TextUtils.isEmpty(nowText) && TextUtils.isEmpty(lastText)) || lastText.equals(nowText)) {
                return false;
            }
            ViewAttributeUtil.setMonitoringFocusKey(view, nowText);
            return true;
        }
        return false;
    }

    public static void changeOn(View view) {
        if (!GrowingTracker.isInitSucceeded()) {
            return;
        }
        Activity activity = ActivityUtil.findActivity(view.getContext());
        if (activity == null || ViewHelper.isIgnoredView(view)) {
            return;
        }

        ViewNode viewNode = getViewNode(view);

        if (viewNode == null) {
            return;
        }

        if (!shouldChangeOn(view, viewNode)) {
            return;
        }

        ViewElementEvent.EventBuilder event = new ViewElementEvent.EventBuilder();
        Page<?> page = PageProvider.get().findPage(viewNode.view);
        event.setEventType(EventType.CHANGE)
                .addElementBuilders(viewNodeToElementBuilders(viewNode))
                .setPageName(page.path())
                .setPageShowTimestamp(page.getShowTimestamp());
        TrackMainThread.trackMain().postEventToTrackMain(event);
    }

    private static List<BaseViewElement.BaseElementBuilder<?>> viewNodeToElementBuilders(ViewNode viewNode) {
        List<BaseViewElement.BaseElementBuilder<?>> mElementBuilders = new ArrayList<BaseViewElement.BaseElementBuilder<?>>();
        mElementBuilders.add((new ViewElement.ElementBuilder())
                .setXpath(viewNode.parentXPath.toStringValue())
                .setTimestamp(System.currentTimeMillis())
                .setIndex(viewNode.lastListPos)
                .setTextValue(viewNode.viewContent)
        );
        return mElementBuilders;
    }

    public static boolean isIgnoredView(View view) {
        IgnorePolicy ignorePolicy = ViewAttributeUtil.getIgnorePolicy(view);
        if (ignorePolicy != null) {
            return ignorePolicy == IgnorePolicy.IGNORE_SELF || ignorePolicy == IgnorePolicy.IGNORE_ALL;
        }
        return isIgnoredByParent(view);
    }

    private static boolean isIgnoredByParent(View view) {
        ViewParent parent = view.getParent();
        if (parent != null && parent instanceof View) {
            IgnorePolicy ignorePolicy = ViewAttributeUtil.getIgnorePolicy((View) parent);
            if ((ignorePolicy == IgnorePolicy.IGNORE_ALL || ignorePolicy == IgnorePolicy.IGNORE_CHILD)) {
                return true;
            }
            return isIgnoredByParent((View) parent);
        }
        return false;
    }
}