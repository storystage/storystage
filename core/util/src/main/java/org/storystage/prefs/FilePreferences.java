/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prefs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * One preferences node as a folder holding a {@code values.properties} file, with child nodes as
 * sub-folders. Every change is written straight away, so nothing is lost if StoryStage is closed
 * abruptly. See {@link FilePreferencesFactory}.
 */
final class FilePreferences extends AbstractPreferences {
  private static final String VALUES_FILE = "values.properties";
  private final File directory;
  private Properties values;

  FilePreferences(FilePreferences parent, String name, File directory) {
    super(parent, name);
    this.directory = directory;
  }

  private Properties values() {
    if (this.values == null) {
      this.values = new Properties();
      File file = new File(this.directory, VALUES_FILE);
      if (file.isFile()) {
        try (InputStream in = new FileInputStream(file)) {
          this.values.load(in);
        } catch (IOException e) {
          // An unreadable file behaves like an empty node; it is rewritten on the next change.
          this.values.clear();
        }
      }
    }
    return this.values;
  }

  private void save() {
    try {
      Files.createDirectories(this.directory.toPath());
      File file = new File(this.directory, VALUES_FILE);
      File temp = new File(this.directory, VALUES_FILE + ".tmp");
      try (OutputStream out = new FileOutputStream(temp)) {
        this.values().store(out, "StoryStage preferences");
      }
      Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException("StoryStage couldn't save a preference in " + this.directory, e);
    }
  }

  @Override
  protected void putSpi(String key, String value) {
    this.values().setProperty(key, value);
    this.save();
  }

  @Override
  protected String getSpi(String key) {
    return this.values().getProperty(key);
  }

  @Override
  protected void removeSpi(String key) {
    if (this.values().remove(key) != null) {
      this.save();
    }
  }

  @Override
  protected void removeNodeSpi() throws BackingStoreException {
    File file = new File(this.directory, VALUES_FILE);
    if (file.exists() && !file.delete()) {
      throw new BackingStoreException("Couldn't delete " + file);
    }
    this.directory.delete();
    this.values = new Properties();
  }

  @Override
  protected String[] keysSpi() {
    return this.values().stringPropertyNames().toArray(new String[0]);
  }

  @Override
  protected String[] childrenNamesSpi() {
    List<String> names = new ArrayList<>();
    File[] children = this.directory.listFiles(File::isDirectory);
    if (children != null) {
      for (File child : children) {
        names.add(URLDecoder.decode(child.getName(), StandardCharsets.UTF_8));
      }
    }
    return names.toArray(new String[0]);
  }

  @Override
  protected AbstractPreferences childSpi(String name) {
    String folder = URLEncoder.encode(name, StandardCharsets.UTF_8);
    return new FilePreferences(this, name, new File(this.directory, folder.isEmpty() ? "_" : folder));
  }

  @Override
  protected void syncSpi() {
    // Re-read on next access, to pick up changes from another running copy.
    this.values = null;
  }

  @Override
  protected void flushSpi() {
    // Every change is already written.
  }
}
