/*******************************************************************************
 * Copyright (c) 2006, 2015, Carnegie Mellon University. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Products derived from the software may not be called "Alice", nor may
 *    "Alice" appear in their name, without prior written permission of
 *    Carnegie Mellon University.
 *
 * 4. All advertising materials mentioning features or use of this software must
 *    display the following acknowledgement: "This product includes software
 *    developed by Carnegie Mellon University"
 *
 * 5. The gallery of art assets and animations provided with this software is
 *    contributed by Electronic Arts Inc. and may be used for personal,
 *    non-commercial, and academic use only. Redistributions of any program
 *    source code that utilizes The Sims 2 Assets must also retain the copyright
 *    notice, list of conditions and the disclaimer contained in
 *    The Alice 3.0 Art Gallery License.
 *
 * DISCLAIMER:
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 * ANY AND ALL EXPRESS, STATUTORY OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY,  FITNESS FOR A
 * PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT ARE DISCLAIMED. IN NO EVENT
 * SHALL THE AUTHORS, COPYRIGHT OWNERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING FROM OR OTHERWISE RELATING TO
 * THE USE OF OR OTHER DEALINGS WITH THE SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package org.alice.ide.issue;

import edu.cmu.cs.dennisc.javax.swing.option.Dialogs;
import edu.cmu.cs.dennisc.jira.JIRAReport;
import org.alice.ide.croquet.models.help.views.AbstractIssueView;
import org.alice.ide.issue.swing.views.ProgressPane;
import org.storystage.StoryStage;

import javax.swing.JOptionPane;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SubmitReportUtilities {
  // StoryStage: reports go to the StoryStage GitHub issues, not to the Alice team
  public static final String REPORT_TITLE = "StoryStage %s (Alice %s) %s: %s";
  public static final String REPORT_BODY = "Description\n\n%s\n\nSteps\n\n%s\n\nThread\n\n```\n%s\n```\n\nEnvironment\n\n%s";
  private static final String KEY_PATTERN = "sk-ant-[A-Za-z0-9_\\-]{20,}";

  private SubmitReportUtilities() {
    throw new AssertionError();
  }

  // TODO Restore REST bug submission and then remove the email submission work around
  public static final boolean USE_REST_INTERFACE = false;

  public static <V extends AbstractIssueView> ProgressPane submitReport(JIRAReport report, Component root) {
    if (USE_REST_INTERFACE) {
        return submitBugByRest(report, root);
    }
    return submitBugToGitHub(report, root);
  }


  private static ProgressPane submitBugByRest(JIRAReport report, Component root) {
    ProgressPane progressPane = new ProgressPane();
    progressPane.initializeAndExecuteWorker(report);

    Component owner = null;
    String title = "Uploading Bug Report";
    String text = "run in background";
    int result = JOptionPane.showOptionDialog(owner, progressPane, title, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new String[] {text}, text);
    switch (result) {
    case JOptionPane.OK_OPTION:
      System.out.println("background");
      break;
    case JOptionPane.CLOSED_OPTION:
      System.out.println("closed");
      break;
    }
    if (root == null) {
      return progressPane;
    }
    if (progressPane.isDone()) {
      if (progressPane.isSuccessful()) {
        JOptionPane.showMessageDialog(root, "Your bug report has been successfully submitted.  Thank you.");
        root.setVisible(false);
      } else {
        JOptionPane.showMessageDialog(root, "Your bug report FAILED to submit.  Thank you for trying.");
      }
    } else {
      root.setVisible(false);
    }
    return progressPane;
  }

  private static ProgressPane submitBugToGitHub(JIRAReport report, Component root) {
    ProgressPane progressPane = new ProgressPane();
    String title = REPORT_TITLE.formatted(StoryStage.VERSION, StoryStage.getAliceBaseVersion(), report.getType(), report.getTruncatedSummary());
    String body = REPORT_BODY.formatted(report.getDescription(), report.getSteps(), report.getException(), report.getEnvironment());
    // Never pass an API key on, even if one appears in an error message.
    body = body.replaceAll(KEY_PATTERN, "sk-ant-(hidden)");
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(body), null);
    String url = StoryStage.NEW_ISSUE_URL + "?title=" + URLEncoder.encode(title, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    Dialogs.showInfo("Report a problem", "The details of this problem have been copied to the clipboard.\n\n"
        + "On the page that opens, paste them into the description and add anything else that helps.\n"
        + "If no page opens, go to:\n" + StoryStage.NEW_ISSUE_URL + "\n\n"
        + "Reports are public, so leave out names and other personal details.");
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(new URI(url));
      }
      if (root != null) {
        root.setVisible(false);
      }
      progressPane.done(true);
    } catch (URISyntaxException | IOException e) {
      progressPane.done(false);
    }
    return progressPane;
  }
}
