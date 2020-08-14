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

package com.growingio.android.sdk.autotrack.models;

import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ExpandableListView;

import com.growingio.android.sdk.autotrack.page.Page;
import com.growingio.android.sdk.autotrack.util.Util;
import com.growingio.android.sdk.autotrack.util.ViewAttributeUtil;
import com.growingio.android.sdk.autotrack.util.ViewHelper;
import com.growingio.android.sdk.autotrack.util.WindowHelper;
import com.growingio.android.sdk.track.utils.ClassExistHelper;
import com.growingio.android.sdk.track.utils.LinkedString;

import java.util.List;

public class ViewNode {
    public static final String ANONYMOUS_CLASS_NAME = "Anonymous";
    private static final String TAG = "GIO.ViewNode";
    public View view;
    public int lastListPos = -1;
    public boolean fullScreen;
    public boolean hasListParent;
    public boolean inClickableGroup;
    public LinkedString parentXPath;
    public LinkedString originalParentXpath;
    public String windowPrefix;
    public String bannerText;
    public String viewContent;
    public boolean parentIdSettled = false;
    public LinkedString clickableParentXPath;
    private int mHashCode = -1;

    private ViewNode() {
    }

    @Override
    public int hashCode() {
        if (mHashCode == -1) {
            int result = 17;
            result = result * 31 + (viewContent != null ? viewContent.hashCode() : 0);
            result = result * 31 + (parentXPath != null ? parentXPath.hashCode() : 0);
            result = result * 31 + lastListPos;
            mHashCode = result;
        }
        return mHashCode;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ViewNode && object.hashCode() == this.hashCode();
    }

    public ViewNode appendNode(View view) {
        if (this.view instanceof ViewGroup) {
            return appendNode(view, ((ViewGroup) this.view).indexOfChild(view));
        }

        return this;
    }

    public ViewNode appendNode(View view, int index) {
        ViewNodeBuilder viewNodeBuilder = ViewNodeBuilder.newViewNode()
                .setView(view)
                .setLastListPos(lastListPos)
                .setFullScreen(fullScreen)
                .setHasListParent(hasListParent || Util.isListView(this.view))
                .setInClickableGroup(inClickableGroup || Util.isViewClickable(this.view))
                .setParentIdSettled(parentIdSettled)
                .setOriginalParentXpath(LinkedString.copy(originalParentXpath))
                .setParentXPath(LinkedString.copy(parentXPath))
                .setWindowPrefix(windowPrefix)
                .setViewName(Util.getSimpleClassName(view.getClass()))
                .setViewIndex(index)
                .setClickableParentXPath(Util.isViewClickable(this.view) ? parentXPath : clickableParentXPath);
        viewNodeBuilder.resetValues();
        return viewNodeBuilder.build();
    }

    public static final class ViewNodeBuilder {
        private View mView;
        private int mLastListPos = -1;
        private boolean mFullScreen;
        private boolean mHasListParent;
        private boolean mInClickableGroup;
        private LinkedString mParentXPath;
        private LinkedString mOriginalParentXpath;
        private String mWindowPrefix;
        private String mBannerText;
        private String mViewContent;
        private boolean mParentIdSettled = false;
        private LinkedString mClickableParentXPath;

        private int mViewIndex = 0;
        private int mViewPosition = 0;
        private String mViewName;

        private ViewNodeBuilder() {
        }

        public static ViewNodeBuilder newViewNode() {
            return new ViewNodeBuilder();
        }

        public ViewNodeBuilder setView(View mView) {
            this.mView = mView;
            return this;
        }

        public ViewNodeBuilder setLastListPos(int mLastListPos) {
            this.mLastListPos = mLastListPos;
            return this;
        }

        public ViewNodeBuilder setFullScreen(boolean mFullScreen) {
            this.mFullScreen = mFullScreen;
            return this;
        }

        public ViewNodeBuilder setHasListParent(boolean mHasListParent) {
            this.mHasListParent = mHasListParent;
            return this;
        }

        public ViewNodeBuilder setInClickableGroup(boolean mInClickableGroup) {
            this.mInClickableGroup = mInClickableGroup;
            return this;
        }

        public ViewNodeBuilder setParentXPath(LinkedString mParentXPath) {
            this.mParentXPath = mParentXPath;
            return this;
        }

        public ViewNodeBuilder setOriginalParentXpath(LinkedString mOriginalParentXpath) {
            this.mOriginalParentXpath = mOriginalParentXpath;
            return this;
        }

        public ViewNodeBuilder setWindowPrefix(String mWindowPrefix) {
            this.mWindowPrefix = mWindowPrefix;
            return this;
        }

        public ViewNodeBuilder setBannerText(String mBannerText) {
            this.mBannerText = mBannerText;
            return this;
        }

        public ViewNodeBuilder setViewContent(String mViewContent) {
            this.mViewContent = mViewContent;
            return this;
        }

        public ViewNodeBuilder setParentIdSettled(boolean mParentIdSettled) {
            this.mParentIdSettled = mParentIdSettled;
            return this;
        }

        public ViewNodeBuilder setClickableParentXPath(LinkedString mClickableParentXPath) {
            this.mClickableParentXPath = mClickableParentXPath;
            return this;
        }

        public ViewNodeBuilder setViewIndex(int mViewIndex) {
            this.mViewIndex = mViewIndex;
            return this;
        }

        public ViewNodeBuilder setViewName(String mViewName) {
            this.mViewName = mViewName;
            return this;
        }

        /**
         * 函数用于更新子结点，函数中所有变量均为appenNode中新建ViewNode的变量(即子结点)
         */
        private void resetValues() {
            viewPosition();
            calcXPath();
            viewContent();
        }

        private void viewPosition() {
            int idx = mViewIndex;
            if (mView.getParent() != null && (mView.getParent() instanceof ViewGroup)) {
                ViewGroup parent = (ViewGroup) mView.getParent();
                if (ClassExistHelper.instanceOfAndroidXViewPager(parent)) {
                    idx = ((androidx.viewpager.widget.ViewPager) parent).getCurrentItem();
                } else if (ClassExistHelper.instanceOfSupportViewPager(parent)) {
                    idx = ((ViewPager) parent).getCurrentItem();
                } else if (parent instanceof AdapterView) {
                    AdapterView listView = (AdapterView) parent;
                    idx = listView.getFirstVisiblePosition() + mViewIndex;
                } else if (ClassExistHelper.instanceOfRecyclerView(parent)) {
                    int adapterPosition = ViewHelper.getChildAdapterPositionInRecyclerView(mView, parent);
                    if (adapterPosition >= 0) {
                        idx = adapterPosition;
                    }
                }
            }
            mViewPosition = idx;
        }

        private void calcXPath() {
            Object parentObject = mView.getParent();
            if (parentObject == null || (WindowHelper.isDecorView(mView) && !(parentObject instanceof View))) {
                return;
            }
            String viewName = ViewAttributeUtil.getViewNameKey(mView);
            String customId = ViewAttributeUtil.getCustomId(mView);
            Page<?> page = ViewAttributeUtil.getViewPage(mView);
            if (viewName != null) {
                mOriginalParentXpath = LinkedString.fromString("/").append(viewName);
                mParentXPath.append("/").append(viewName);
                return;
            } else if (customId != null) {
                mOriginalParentXpath = LinkedString.fromString("/").append(customId);
                mParentXPath = LinkedString.fromString("/").append(customId);
                return;
            } else if (page != null && !page.isIgnored()) {
                mOriginalParentXpath = LinkedString.fromString(WindowHelper.getWindowPrefix(mView));
                mParentXPath = LinkedString.fromString(WindowHelper.getWindowPrefix(mView));
            }

            if (parentObject instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) parentObject;

                if (parent instanceof ExpandableListView) {
                    // 处理ExpandableListView
                    ExpandableListView listParent = (ExpandableListView) parent;
                    long elp = ((ExpandableListView) mView.getParent()).getExpandableListPosition(mViewPosition);
                    if (ExpandableListView.getPackedPositionType(elp) == ExpandableListView.PACKED_POSITION_TYPE_NULL) {
                        mHasListParent = false;
                        if (mViewPosition < listParent.getHeaderViewsCount()) {
                            mOriginalParentXpath.append("/ELH[").append(mViewPosition).append("]/").append(mViewName).append("[0]");
                            mParentXPath.append("/ELH[").append(mViewPosition).append("]/").append(mViewName).append("[0]");
                        } else {
                            int footerIndex = mViewPosition - (listParent.getCount() - listParent.getFooterViewsCount());
                            mOriginalParentXpath.append("/ELF[").append(footerIndex).append("]/").append(mViewName).append("[0]");
                            mParentXPath.append("/ELF[").append(footerIndex).append("]/").append(mViewName).append("[0]");
                        }
                    } else {
                        int groupIdx = ExpandableListView.getPackedPositionGroup(elp);
                        int childIdx = ExpandableListView.getPackedPositionChild(elp);
                        if (childIdx != -1) {
                            mLastListPos = childIdx;
                            mParentXPath = LinkedString.fromString(mOriginalParentXpath.toStringValue()).append("/ELVG[").append(groupIdx).append("]/ELVC[-]/").append(mViewName).append("[0]");
                            mOriginalParentXpath.append("/ELVG[").append(groupIdx).append("]/ELVC[").append(childIdx).append("]/").append(mViewName).append("[0]");
                        } else {
                            mLastListPos = groupIdx;
                            mParentXPath = LinkedString.fromString(mOriginalParentXpath.toStringValue()).append("/ELVG[-]/").append(mViewName).append("[0]");
                            mOriginalParentXpath.append("/ELVG[").append(groupIdx).append("]/").append(mViewName).append("[0]");
                        }
                    }
                } else if (Util.isListView(parent) || ClassExistHelper.instanceOfRecyclerView(parent)) {
                    // 处理有特殊的position的元素
                    List bannerTag = ViewAttributeUtil.getBannerKey(parent);
                    if (bannerTag != null && bannerTag.size() > 0) {
                        mViewPosition = Util.calcBannerItemPosition(bannerTag, mViewPosition);
                        mBannerText = Util.truncateViewContent(String.valueOf(bannerTag.get(mViewPosition)));
                    }
                    mLastListPos = mViewPosition;
                    mParentXPath = LinkedString.fromString(mOriginalParentXpath.toStringValue()).append("/").append(mViewName).append("[-]");
                    mOriginalParentXpath.append("/").append(mViewName).append("[").append(mLastListPos).append("]");
                } else if (ClassExistHelper.instanceofAndroidXSwipeRefreshLayout(parentObject)
                        || ClassExistHelper.instanceOfSupportSwipeRefreshLayout(parentObject)) {
                    mOriginalParentXpath.append("/").append(mViewName).append("[0]");
                    mParentXPath.append("/").append(mViewName).append("[0]");
                } else {
                    int matchTypePosition = 0;
                    String matchType = mView.getClass().getSimpleName();
                    boolean findChildView = false;
                    for (int siblingIndex = 0; siblingIndex < parent.getChildCount(); siblingIndex++) {
                        View siblingView = parent.getChildAt(siblingIndex);
                        if (siblingView == mView) {
                            findChildView = true;
                            break;
                        } else if (siblingView.getClass().getSimpleName().equals(matchType)) {
                            matchTypePosition++;
                        }
                    }
                    if (findChildView) {
                        mOriginalParentXpath.append("/").append(mViewName).append("[").append(matchTypePosition).append("]");
                        mParentXPath.append("/").append(mViewName).append("[").append(matchTypePosition).append("]");
                    } else {
                        mOriginalParentXpath.append("/").append(mViewName).append("[").append(mViewPosition).append("]");
                        mParentXPath.append("/").append(mViewName).append("[").append(mViewPosition).append("]");
                    }
                }
            } else {
                mOriginalParentXpath.append("/").append(mViewName).append("[").append(mViewPosition).append("]");
                mParentXPath.append("/").append(mViewName).append("[").append(mViewPosition).append("]");
            }

            String id = Util.getIdName(mView, mParentIdSettled);
            if (id != null) {
                if (ViewAttributeUtil.getViewId(mView) != null) {
                    mParentIdSettled = true;
                }
                mOriginalParentXpath.append("#").append(id);
                mParentXPath.append("#").append(id);
            }
        }

        private void viewContent() {
            mViewContent = Util.getViewContent(mView, mBannerText);
        }

        public ViewNode build() {
            ViewNode viewNode = new ViewNode();
            viewNode.viewContent = this.mViewContent;
            viewNode.lastListPos = this.mLastListPos;
            viewNode.inClickableGroup = this.mInClickableGroup;
            viewNode.clickableParentXPath = this.mClickableParentXPath;
            viewNode.view = this.mView;
            viewNode.windowPrefix = this.mWindowPrefix;
            viewNode.parentXPath = this.mParentXPath;
            viewNode.hasListParent = this.mHasListParent;
            viewNode.fullScreen = this.mFullScreen;
            viewNode.bannerText = this.mBannerText;
            viewNode.originalParentXpath = this.mOriginalParentXpath;
            viewNode.parentIdSettled = this.mParentIdSettled;
            return viewNode;
        }
    }
}