/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

/**
 * Removes the Markdown the panel can't show: "**" bold marks and "#" heading marks at the start
 * of a line. Works on streamed text, where a "**" can be split across two pieces. The system
 * prompt asks for plain text; this is the safety net (TR1 showed raw **bold** in the transcript).
 */
final class PlainText {
  private boolean pendingStar;
  private boolean atLineStart = true;
  private boolean skippedHash;

  /** One streamed piece in, the text to show out (possibly holding back a single '*'). */
  String feed(String piece) {
    StringBuilder out = new StringBuilder(piece.length());
    for (int i = 0; i < piece.length(); i++) {
      char c = piece.charAt(i);
      if (c == '*') {
        if (this.pendingStar) {
          this.pendingStar = false;
        } else {
          this.pendingStar = true;
        }
        continue;
      }
      if (this.pendingStar) {
        out.append('*');
        this.pendingStar = false;
      }
      if (this.atLineStart && c == '#') {
        this.skippedHash = true;
        continue;
      }
      if (this.skippedHash && c == ' ') {
        // The space after a heading's "#" marks.
        this.skippedHash = false;
        continue;
      }
      this.skippedHash = false;
      out.append(c);
      this.atLineStart = c == '\n';
    }
    return out.toString();
  }

  /** Whatever was held back at the end of a reply. */
  String flush() {
    String rv = this.pendingStar ? "*" : "";
    this.pendingStar = false;
    this.atLineStart = true;
    this.skippedHash = false;
    return rv;
  }

  /** A whole string at once. */
  static String of(String text) {
    if (text == null) {
      return null;
    }
    PlainText filter = new PlainText();
    return filter.feed(text) + filter.flush();
  }
}
