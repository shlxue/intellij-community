/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogFilterer;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiImpl;

public class RefreshLogAction extends RefreshAction {
  private static final Logger LOG = Logger.getInstance(RefreshLogAction.class);

  public RefreshLogAction() {
    super("Refresh", "Re-read Commits From Disk for All VCS Roots and Rebuild Log", AllIcons.Actions.Refresh);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VcsLogUtil.triggerUsage(e);

    VcsLogManager logManager = e.getRequiredData(VcsLogDataKeys.LOG_MANAGER);

    // diagnostic for possible refresh problems
    VcsLogUi ui = e.getRequiredData(com.intellij.vcs.log.VcsLogDataKeys.VCS_LOG_UI);
    if (ui instanceof VcsLogUiImpl) {
      VcsLogFilterer filterer = ((VcsLogUiImpl)ui).getFilterer();
      if (!filterer.isValid()) {
        String message = "Trying to refresh invalid log tab.";
        if (!logManager.getDataManager().getProgress().isRunning()) {
          LOG.error(message);
        } else {
          LOG.warn(message);
        }
        filterer.setValid(true);
      }
    }

    logManager.getDataManager().refreshSoftly();
  }

  @Override
  public void update(AnActionEvent e) {
    VcsLogManager logManager = e.getData(VcsLogDataKeys.LOG_MANAGER);
    e.getPresentation().setEnabledAndVisible(logManager != null && e.getData(com.intellij.vcs.log.VcsLogDataKeys.VCS_LOG_UI) != null);
  }
}
