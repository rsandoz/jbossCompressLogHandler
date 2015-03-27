package loggers;

import org.jboss.logmanager.ExtLogRecord;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;

import java.util.logging.ErrorManager;
import org.jboss.logmanager.handlers.FileHandler;
import org.jboss.logmanager.PropertyConfigurator;

public class CompressHandler extends FileHandler {

  private SimpleDateFormat format;
  private String nextSuffix;
  private Period period = Period.NEVER;
  private long nextRollover = Long.MAX_VALUE;
  private TimeZone timeZone = TimeZone.getDefault();
  private int maxBackupIndex = 1;
  private String path = "";

  public CompressHandler() {
  }

  public CompressHandler(final String fileName) throws FileNotFoundException {
    super(fileName);
  }

  public CompressHandler(final String fileName, final boolean append) throws FileNotFoundException {
    super(fileName, append);
  }

  public CompressHandler(final File file, final String suffix) throws FileNotFoundException {
    super(file);
    setSuffix(suffix);
  }

  public CompressHandler(final File file, final String suffix, final boolean append) throws FileNotFoundException {
    super(file, append);
    setSuffix(suffix);
  }

  @Override
  protected void preWrite(final ExtLogRecord record) {
    final long recordMillis = record.getMillis();
    if (recordMillis >= nextRollover) {
      rollOver();
      calcNextRollover(recordMillis);
    }
  }

  public void setMaxBackupIndex(final int maxBackupIndex) {
    checkAccess();
    synchronized (outputLock) {
      this.maxBackupIndex = maxBackupIndex;
    }
  }

  public void setSuffix(String suffix) throws IllegalArgumentException {
    final SimpleDateFormat format = new SimpleDateFormat(suffix);
    format.setTimeZone(timeZone);
    final int len = suffix.length();
    Period period = Period.NEVER;
    for (int i = 0; i < len; i ++) {
      switch (suffix.charAt(i)) {
        case 'y': period = min(period, Period.YEAR); break;
        case 'M': period = min(period, Period.MONTH); break;
        case 'w':
        case 'W': period = min(period, Period.WEEK); break;
        case 'D':
        case 'd':
        case 'F':
        case 'E': period = min(period, Period.DAY); break;
        case 'a': period = min(period, Period.HALF_DAY); break;
        case 'H':
        case 'k':
        case 'K':
        case 'h': period = min(period, Period.HOUR); break;
        case 'm': period = min(period, Period.MINUTE); break;
        case '\'': while (suffix.charAt(++i) != '\''); break;
        case 's':
        case 'S': throw new IllegalArgumentException("Rotating by second or millisecond is not supported");
      }
    } synchronized (outputLock) {
      this.format = format;
      this.period = period;
      final long now = System.currentTimeMillis();
      calcNextRollover(now);
    }
  }

  private void compress(File backupfile) throws FileNotFoundException {
    ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(backupfile.getAbsolutePath()+".zip")));
    FileInputStream fis = new FileInputStream(backupfile);
    try {
      try {
        zos.putNextEntry(new ZipEntry(backupfile.getName()));
        byte[] bytes = new byte[1024];
        int read = 0;
        while ((read = fis.read(bytes)) != -1)
          zos.write(bytes, 0, read);
        zos.closeEntry();
      } finally {
        fis.close();
        zos.close();
      }
      backupfile.delete();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void clean(final File file) {
    File parentDirectory = file.getAbsoluteFile().getParentFile();
    File[] fileList = parentDirectory.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.startsWith(file.getName());
      }
    });
    for (int i = 0; i < fileList.length - maxBackupIndex; i++)
      fileList[i].delete();
  }

  private void rollOver() {
    try {
      final File file = getFile();
      // first, close the original file (some OSes won't let you move/rename a file that is open)
      setFile(null);
      // next, rotate it
      File backupfile = new File(file.getAbsolutePath() + nextSuffix);
      file.renameTo(backupfile);
      // then, compress it
      compress(backupfile);
      // then, clear out oldest
      clean(file);
      // start new file
      setFile(file);
    } catch (FileNotFoundException e) {
      reportError("Unable to rotate log file", e, ErrorManager.OPEN_FAILURE);
    }
  }

  private void calcNextRollover(final long fromTime) {
    if (period == Period.NEVER) {
      nextRollover = Long.MAX_VALUE;
      return;
    }
    nextSuffix = format.format(new Date(fromTime));
    final Calendar calendar = Calendar.getInstance(timeZone);
    calendar.setTimeInMillis(fromTime);
    final Period period = this.period;
    // clear out less-significant fields
    switch (period) {
        default:
        case YEAR:
            calendar.clear(Calendar.MONTH);
        case MONTH:
            calendar.clear(Calendar.DAY_OF_MONTH);
            calendar.clear(Calendar.WEEK_OF_MONTH);
        case WEEK:
            calendar.clear(Calendar.DAY_OF_WEEK);
            calendar.clear(Calendar.DAY_OF_WEEK_IN_MONTH);
        case DAY:
        	calendar.set(Calendar.HOUR_OF_DAY, 0);
        case HALF_DAY:
            calendar.clear(Calendar.HOUR);
        case HOUR:
            calendar.clear(Calendar.MINUTE);
        case MINUTE:
            calendar.clear(Calendar.SECOND);
            calendar.clear(Calendar.MILLISECOND);
    }
    // increment the relevant field
    switch (period) {
        case YEAR:
            calendar.add(Calendar.YEAR, 1);
            break;
        case MONTH:
            calendar.add(Calendar.MONTH, 1);
            break;
        case WEEK:
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
            break;
        case DAY:
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            break;
        case HALF_DAY:
            calendar.add(Calendar.AM_PM, 1);
            break;
        case HOUR:
            calendar.add(Calendar.HOUR, 1);
            break;
        case MINUTE:
            calendar.add(Calendar.MINUTE, 1);
            break;
    }
    nextRollover = calendar.getTimeInMillis();
  }

  public TimeZone getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(final TimeZone timeZone) {
    if (timeZone == null)
      throw new NullPointerException("timeZone is null");
    this.timeZone = timeZone;
  }

  private static <T extends Comparable<? super T>> T min(T a, T b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  public enum Period {
      MINUTE
    , HOUR
    , HALF_DAY
    , DAY
    , WEEK
    , MONTH
    , YEAR
    , NEVER
  }

  private static String replaceProperties(String value) {
    final StringBuilder builder = new StringBuilder();
    final char[] chars = value.toCharArray();
    final int len = chars.length;
    int state = 0;
    int start = -1;
    int nameStart = -1;
    for (int i = 0; i < len; i ++) {
      char ch = chars[i];
      switch (state) {
        case INITIAL: {
          switch (ch) {
            case '$': {
              state = GOT_DOLLAR;
              continue;
            }
            default: {
              builder.append(ch);
              continue;
            }
          }
          // not reachable
        }
        
        case GOT_DOLLAR: {
          switch (ch) {
            case '$': {
              builder.append(ch);
              state = INITIAL;
              continue;
            }
            case '{': {
              start = i + 1;
              nameStart = start;
              state = GOT_OPEN_BRACE;
              continue;
            }
            default: {
              // invalid; emit and resume
              builder.append('$').append(ch);
              state = INITIAL;
              continue;
            }
          }
          // not reachable
        }
  
        case GOT_OPEN_BRACE: {
          switch (ch) {
            case ':':
            case '}':
            case ',': {
              final String name = value.substring(nameStart, i).trim();
              if ("/".equals(name)) {
                builder.append(File.separator);
                state = ch == '}' ? INITIAL : RESOLVED;
                continue;
              } else if (":".equals(name)) {
                builder.append(File.pathSeparator);
                state = ch == '}' ? INITIAL : RESOLVED;
                continue;
              }
              final String val = System.getProperty(name);
              if (val != null) {
                builder.append(val);
                state = ch == '}' ? INITIAL : RESOLVED;
                continue;
              } else if (ch == ',') {
                nameStart = i + 1;
                continue;
              } else if (ch == ':') {
                start = i + 1;
                state = DEFAULT;
                continue;
              } else {
                builder.append(value.substring(start - 2, i + 1));
                state = INITIAL;
                continue;
              }
            }
            default: {
              continue;
            }
          }
          // not reachable
        }
  
        case RESOLVED: {
          if (ch == '}') {
            state = INITIAL;
          }
          continue;
        }
  
        case DEFAULT: {
          if (ch == '}') {
            state = INITIAL;
            builder.append(value.substring(start, i));
          }
          continue;
        }
        
        default: throw new IllegalStateException();
      }
    }

    switch (state) {
      case GOT_DOLLAR: {
        builder.append('$');
        break;
      }
      case DEFAULT:
      case GOT_OPEN_BRACE: {
        builder.append(value.substring(start - 2));
        break;
      }
    }

    return builder.toString();
  }

  private static final int INITIAL = 0;
  private static final int GOT_DOLLAR = 1;
  private static final int GOT_OPEN_BRACE = 2;
  private static final int RESOLVED = 3;
  private static final int DEFAULT = 4;

  public void setFileName(String fileName) throws FileNotFoundException {
    super.setFileName(replaceProperties(fileName));
  }
}
