package net.hearthstats;


import java.awt.AWTException;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkListener;

import net.hearthstats.analysis.AnalyserEvent;
import net.hearthstats.analysis.HearthstoneAnalyser;
import net.hearthstats.config.Application;
import net.hearthstats.config.Environment;
import net.hearthstats.log.Log;
import net.hearthstats.log.LogPane;
import net.hearthstats.logmonitor.HearthstoneLogMonitor;
import net.hearthstats.notification.DialogNotificationQueue;
import net.hearthstats.notification.NotificationQueue;
import net.hearthstats.state.Screen;
import net.hearthstats.state.ScreenGroup;
import net.hearthstats.ui.ClickableDeckBox;
import net.hearthstats.ui.HelpIcon;
import net.hearthstats.ui.MatchEndPopup;
import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dmurph.tracking.JGoogleAnalyticsTracker;


@SuppressWarnings("serial")
public class Monitor extends JFrame implements Observer {

  private static final String PROFILES_URL = "http://hearthstats.net/profiles";
  private static final String DECKS_URL = "http://hearthstats.net/decks";
  private static final int POLLING_INTERVAL_IN_MS = 100;
  private static final EnumSet<Screen> DO_NOT_NOTIFY_SCREENS = EnumSet.of(Screen.COLLECTION, Screen.COLLECTION_ZOOM, Screen.MAIN_TODAYSQUESTS, Screen.TITLE);

  private static Logger debugLog = LoggerFactory.getLogger(Monitor.class);

  public static final String[] hsClassOptions = {
    "- undetected -",
    "Druid",
    "Hunter",
    "Mage",
    "Paladin",
    "Priest",
    "Rogue",
    "Shaman",
    "Warlock",
    "Warrior"
  };

  private final Environment environment;
  private final HearthstoneAnalyser analyzer;

  protected API api = new API();
  protected ProgramHelper hsHelper = ConfigDeprecated.programHelper();
  protected HearthstoneLogMonitor hearthstoneLogMonitor;
  protected boolean _drawPaneAdded = false;
  protected BufferedImage image;
  protected NotificationQueue notificationQueue;
  private HyperlinkListener hyperLinkListener = HyperLinkHandler.getInstance();
  private JTextField currentOpponentNameField;
  private JLabel currentMatchLabel;
  private JCheckBox currentGameCoinField;
  private JTextArea currentNotesField;
  private JButton lastMatchButton;
  private HearthstoneMatch lastMatch;
  private JComboBox<String> deckSlot1Field;
  private JComboBox<String> deckSlot2Field;
  private JComboBox<String> deckSlot3Field;
  private JComboBox<String> deckSlot4Field;
  private JComboBox<String> deckSlot5Field;
  private JComboBox<String> deckSlot6Field;
  private JComboBox<String> deckSlot7Field;
  private JComboBox<String> deckSlot8Field;
  private JComboBox<String> deckSlot9Field;
  private JComboBox currentOpponentClassSelect;
  private JComboBox currentYourClassSelector;
  private boolean hearthstoneDetected;
  private JGoogleAnalyticsTracker analytics;
  private LogPane logText;
  private JScrollPane logScroll;
  private JTextField userKeyField;
  private JComboBox monitoringMethodField;
  private JCheckBox checkUpdatesField;
  private JCheckBox notificationsEnabledField;
  private JComboBox notificationsFormat;
  private JCheckBox showHsFoundField;
  private JCheckBox showHsClosedField;
  private JCheckBox showScreenNotificationField;
  private JCheckBox showModeNotificationField;
  private JCheckBox showDeckNotificationField;
  private JComboBox showMatchPopupField;
  private JCheckBox analyticsField;
  private JCheckBox minToTrayField;
  private JCheckBox startMinimizedField;
  private JCheckBox showYourTurnNotificationField;
  private JCheckBox showDeckOverlay;
  private JTabbedPane tabbedPane;
  private ResourceBundle bundle = ResourceBundle.getBundle("net.hearthstats.resources.Main");
  private Boolean currentMatchEnabled = false;
  private boolean playingInMatch = false;

  //http://stackoverflow.com/questions/7461477/how-to-hide-a-jframe-in-system-tray-of-taskbar
  TrayIcon trayIcon;
  SystemTray tray;
  private Thread poller = new Thread(new Runnable() {
    @Override
    public void run() {
      pollHsImpl();
    }
  });

  protected JPanel drawPane = new JPanel() {
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.drawImage(image, 0, 0, null);
    }
  };



  public Monitor(Environment environment) throws HeadlessException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    this.environment = environment;
    this.analyzer = new HearthstoneAnalyser(environment);
    notificationQueue = environment.newNotificationQueue();
  }


  /**
   * Loads text from the main resource bundle, using the local language when available.
   *
   * @param key the key for the desired string
   * @return The requested string
   */
  private String t(String key) {
    return bundle.getString(key);
  }


  /**
   * Loads text from the main resource bundle, using the local language when available, and puts the given value into the appropriate spot.
   *
   * @param key    the key for the desired string
   * @param value0 a value to place in the {0} placeholder in the string
   * @return The requested string
   */
  private String t(String key, String value0) {
    String message = bundle.getString(key);
    return MessageFormat.format(message, value0);
  }


  public void start() throws IOException {
    if (ConfigDeprecated.analyticsEnabled()) {
      debugLog.debug("Enabling analytics");
      analytics = AnalyticsTracker.tracker();
      analytics.trackEvent("app", "AppStart");
    }

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        handleClose();
      }
    });

    createAndShowGui();
    showWelcomeLog();
    checkForUpdates();

    api.addObserver(this);
    analyzer.addObserver(this);
    hsHelper.addObserver(this);


    if (checkForUserKey()) {
      poller.start();
    } else {
      System.exit(1);
    }

    if (ConfigDeprecated.os == ConfigDeprecated.OS.OSX) {
      Log.info(t("waiting_for_hs"));
    } else {
      Log.info(t("waiting_for_hs_windowed"));
    }
  }


  public void handleClose() {
    Point p = getLocationOnScreen();
    ConfigDeprecated.setX(p.x);
    ConfigDeprecated.setY(p.y);
    Dimension rect = getSize();
    ConfigDeprecated.setWidth((int) rect.getWidth());
    ConfigDeprecated.setHeight((int) rect.getHeight());
    try {
      ConfigDeprecated.save();
    } catch (Throwable t) {
      Log.warn(
        "Error occurred trying to write settings file, your settings may not be saved",
        t);
    }
    System.exit(0);
  }


  private void showWelcomeLog() {
    debugLog.debug("Showing welcome log messages");

    Log.welcome("HearthStats.net " + t("Uploader") + " v" + Application.version() + "-" + environment.os());

    Log.help(t("welcome_1_set_decks"));
    if (ConfigDeprecated.os == ConfigDeprecated.OS.OSX) {
      Log.help(t("welcome_2_run_hearthstone"));
      Log.help(t("welcome_3_notifications"));
    } else {
      Log.help(t("welcome_2_run_hearthstone_windowed"));
      Log.help(t("welcome_3_notifications_windowed"));
    }
    String logFileLocation = Log.getLogFileLocation();
    if (logFileLocation == null) {
      Log.help(t("welcome_4_feedback"));
    } else {
      Log.help(t("welcome_4_feedback_with_log", logFileLocation));
    }

  }


  private boolean checkForUserKey() {
    boolean userKeySet = !ConfigDeprecated.getUserKey().equals("your_userkey_here");
    if (userKeySet) {
      return true;
    } else {
      Log.warn(t("error.userkey_not_entered"));

      bringWindowToFront();

      JOptionPane.showMessageDialog(this,
        "HearthStats.net " + t("error.title") + ":\n\n" +
          t("you_need_to_enter_userkey") + "\n\n" +
          t("get_it_at_hsnet_profiles"));

      Desktop d = Desktop.getDesktop();
      try {
        d.browse(new URI(PROFILES_URL));
      } catch (IOException | URISyntaxException e) {
        Log.warn("Error launching browser with URL " + PROFILES_URL, e);
      }

      String userkey = JOptionPane.showInputDialog(this,
        t("enter_your_userkey"));
      if (StringUtils.isEmpty(userkey)) {
        return false;
      } else {
        ConfigDeprecated.setUserKey(userkey);
        try {
          userKeyField.setText(userkey);
          ConfigDeprecated.save();
          Log.info(t("UserkeyStored"));
        } catch (Throwable e) {
          Log.warn(
            "Error occurred trying to write settings file, your settings may not be saved",
            e);
        }
        return true;
      }
    }
  }


  /**
   * Brings the monitor window to the front of other windows. Should only be used for important events like a
   * modal dialog or error that we want the user to see immediately.
   */
  private void bringWindowToFront() {
    final Monitor frame = this;
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        frame.setVisible(true);
      }
    });
  }


  /**
   * Overridden version of setVisible based on http://stackoverflow.com/questions/309023/how-to-bring-a-window-to-the-front
   * that should ensure the window is brought to the front for important things like modal dialogs.
   */
  @Override
  public void setVisible(final boolean visible) {
    // let's handle visibility...
    if (!visible || !isVisible()) { // have to check this condition simply because super.setVisible(true) invokes toFront if frame was already visible
      super.setVisible(visible);
    }
    // ...and bring frame to the front.. in a strange and weird way
    if (visible) {
      int state = super.getExtendedState();
      state &= ~JFrame.ICONIFIED;
      super.setExtendedState(state);
      super.setAlwaysOnTop(true);
      super.toFront();
      super.requestFocus();
      super.setAlwaysOnTop(false);
    }
  }


  @Override
  public void toFront() {
    super.setVisible(true);
    int state = super.getExtendedState();
    state &= ~JFrame.ICONIFIED;
    super.setExtendedState(state);
    super.setAlwaysOnTop(true);
    super.toFront();
    super.requestFocus();
    super.setAlwaysOnTop(false);
  }


  private void createAndShowGui() {
    debugLog.debug("Creating GUI");

    Image icon = new ImageIcon(getClass().getResource("/images/icon.png")).getImage();
    setIconImage(icon);
    setLocation(ConfigDeprecated.getX(), ConfigDeprecated.getY());
    setSize(ConfigDeprecated.getWidth(), ConfigDeprecated.getHeight());

    tabbedPane = new JTabbedPane();
    add(tabbedPane);

    // log
    logText = new LogPane();
    logScroll = new JScrollPane(logText, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    tabbedPane.add(logScroll, t("tab.log"));

    tabbedPane.add(createMatchUi(), t("tab.current_match"));
    tabbedPane.add(createDecksUi(), t("tab.decks"));
    tabbedPane.add(createOptionsUi(), t("tab.options"));
    tabbedPane.add(createAboutUi(), t("tab.about"));

    tabbedPane.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (tabbedPane.getSelectedIndex() == 2)
          try {
            updateDecksTab();
          } catch (IOException e1) {
            Monitor.this.notify(t("error.loading_decks.title"), t("error.loading_decks"));
            Log.warn(t("error.loading_decks"), e1);
          }
      }
    });

    updateCurrentMatchUi();

    enableMinimizeToTray();

    setMinimumSize(new Dimension(500, 600));
    setVisible(true);

    if (ConfigDeprecated.startMinimized())
      setState(JFrame.ICONIFIED);

    updateTitle();
  }


  private JScrollPane createAboutUi() {

    JPanel panel = new JPanel();
    panel.setMaximumSize(new Dimension(100, 100));
    panel.setBackground(Color.WHITE);
    MigLayout layout = new MigLayout("");
    panel.setLayout(layout);

    JEditorPane text = new JEditorPane();
    text.setContentType("text/html");
    text.setEditable(false);
    text.setBackground(Color.WHITE);
    text.setText("<html><body style=\"font-family:'Helvetica Neue', Helvetica, Arial, sans-serif; font-size:10px;\">" +
        "<h2 style=\"font-weight:normal\"><a href=\"http://hearthstats.net\">HearthStats.net</a> " + t("Uploader") + " v" + Application.version() + "</h2>" +
        "<p><strong>" + t("Author") + ":</strong> <ul>" +
        "<li> Jerome Dane (<a href=\"https://plus.google.com/+JeromeDane\">Google+</a>, <a href=\"http://twitter.com/JeromeDane\">Twitter</a>) </li> " +
        "<li> Charles Gutjahr (<a href=\"http://charlesgutjahr.com\">Website</a>) </li>" +
        "<li> Michel Daviot (<a href=\"https://plus.google.com/+MichelDaviot\">Google+</a></p>, <a href=\"https://github.com/tyrcho\">Github</a>, <a href=\"http://michel-daviot.blogspot.fr\">Blog</a>) </li>" +
        "</ul><p>" + t("about.utility_l1") + "<br>" +
        t("about.utility_l2") + "<br>" +
        t("about.utility_l3") + "</p>" +
        "<p>" + t("about.open_source_l1") + "<br>" +
        t("about.open_source_l2") + "</p>" +
        "<p>&bull; <a href=\"https://github.com/HearthStats/HearthStats.net-Uploader/\">" + t("about.project_source") + "</a><br/>" +
        "&bull; <a href=\"https://github.com/HearthStats/HearthStats.net-Uploader/releases\">" + t("about.releases_and_changelog") + "</a><br/>" +
        "&bull; <a href=\"https://github.com/HearthStats/HearthStats.net-Uploader/issues\">" + t("about.feedback_and_suggestions") + "</a><br/>" +
        "&bull; <a href=\"http://redd.it/1wa4rc/\">Reddit thread</a> (please up-vote)</p>" +
        "<p><strong>" + t("about.support_project") + ":</strong></p>" +
        "</body></html>"
    );
    panel.add(text, "wrap");

    JButton donateButton = new JButton("<html><img style=\"border-style: none;\" src=\"" + getClass().getResource("/images/donate.gif") + "\"/></html>");
    donateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Create Desktop object
        Desktop d = Desktop.getDesktop();
        // Browse a URL, say google.com
        try {
          d.browse(new URI("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=UJFTUHZF6WPDS"));
        } catch (Throwable e1) {
          Main.showErrorDialog("Error launching browser with donation URL", e1);
        }
      }
    });
    donateButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
    panel.add(donateButton, "wrap");

    JEditorPane contribtorsText = new JEditorPane();
    contribtorsText.setContentType("text/html");
    contribtorsText.setEditable(false);
    contribtorsText.setBackground(Color.WHITE);
    contribtorsText.setText("<html><body style=\"font-family:arial,sans-serif; font-size:10px;\">" +
        "<p><strong>Contributors</strong> (listed alphabetically):</p>" +
        "<p>" +
        "&bull; <a href=\"https://github.com/gtch\">Charles Gutjahr</a> - OS X version, new screen detection and card detection in log file<br>" +
        "&bull; <a href=\"https://github.com/jcrka\">jcrka</a> - Russian translation<br>" +
        "&bull; <a href=\"https://github.com/JeromeDane\">Jerome Dane</a> - Original developer<br>" +
        "&bull; <a href=\"https://github.com/sargonas\">J Eckert</a> - Fixed notifications spawning taskbar icons<br>" +
        "&bull; <a href=\"https://github.com/tyrcho\">Michel Daviot</a> - Deck overlay, Maven and Scala implementation<br>" +
        "&bull; <a href=\"https://github.com/nwalsh1995\">nwalsh1995</a> - Started turn detection development<br>" +
        "&bull; <a href=\"https://github.com/remcoros\">Remco Ros</a> (<a href=\"http://hearthstonetracker.com/\">HearthstoneTracker</a>) - Provides advice &amp; suggestins<br>" +
        "&bull; <a href=\"https://github.com/RoiyS\">RoiyS</a> - Added option to disable all notifications<br>" +
        "</p>" +
        "</body></html>"
    );
    contribtorsText.addHyperlinkListener(hyperLinkListener);
    panel.add(contribtorsText, "wrap");

    return new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
  }


  private JPanel createMatchUi() {
    JPanel panel = new JPanel();

    MigLayout layout = new MigLayout();
    panel.setLayout(layout);

    // match label
    panel.add(new JLabel(" "), "wrap");
    currentMatchLabel = new JLabel();
    panel.add(currentMatchLabel, "skip,span,wrap");

    panel.add(new JLabel(" "), "wrap");

    String[] localizedClassOptions = new String[hsClassOptions.length];
    localizedClassOptions[0] = "- " + t("undetected") + " -";
    for (int i = 1; i < localizedClassOptions.length; i++)
      localizedClassOptions[i] = t(hsClassOptions[i]);

    // your class
    panel.add(new JLabel(t("match.label.your_class") + " "), "skip,right");
    currentYourClassSelector = new JComboBox<>(localizedClassOptions);
    panel.add(currentYourClassSelector, "wrap");

    // opponent class
    panel.add(new JLabel(t("match.label.opponents_class") + " "), "skip,right");
    currentOpponentClassSelect = new JComboBox<>(localizedClassOptions);
    panel.add(currentOpponentClassSelect, "wrap");

    // Opponent name
    panel.add(new JLabel("Opponent's Name: "), "skip,right");
    currentOpponentNameField = new JTextField();
    currentOpponentNameField.setMinimumSize(new Dimension(100, 1));
    currentOpponentNameField.addKeyListener(new KeyAdapter() {
      public void keyReleased(KeyEvent e) {
        analyzer.getMatch().opponentName_$eq(
          currentOpponentNameField.getText().replaceAll(
            "(\r\n|\n)", "<br/>"));
      }
    });
    panel.add(currentOpponentNameField, "wrap");


    // coin
    panel.add(new JLabel(t("match.label.coin") + " "), "skip,right");
    currentGameCoinField = new JCheckBox(t("match.coin"));
    currentGameCoinField.setSelected(ConfigDeprecated.showHsClosedNotification());
    currentGameCoinField.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        analyzer.getMatch().coin_$eq(
          currentGameCoinField.isSelected());
      }
    });
    panel.add(currentGameCoinField, "wrap");

    // notes
    panel.add(new JLabel(t("match.label.notes") + " "), "skip,wrap");
    currentNotesField = new JTextArea();
    currentNotesField.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(1, 1, 1, 1, Color.black),
      BorderFactory.createEmptyBorder(3, 6, 3, 6)));
    currentNotesField.setMinimumSize(new Dimension(350, 150));
    currentNotesField.setBackground(Color.WHITE);
    currentNotesField.addKeyListener(new KeyAdapter() {
      public void keyReleased(KeyEvent e) {
        analyzer.getMatch().notes_$eq(currentNotesField.getText());
      }
    });
    panel.add(currentNotesField, "skip,span");

    panel.add(new JLabel(" "), "wrap");

    // last match
    panel.add(new JLabel(t("match.label.previous_match") + " "), "skip,wrap");
    lastMatchButton = new JButton("[n/a]");
    lastMatchButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        String url = "Arena".equals(lastMatch.mode()) ? "http://hearthstats.net/arenas/new"
          : lastMatch.editUrl();
        try {
          Desktop.getDesktop().browse(new URI(url));
        } catch (Throwable e) {
          Main.showErrorDialog("Error launching browser with URL " + url, e);
        }
      }
    });
    lastMatchButton.setEnabled(false);
    panel.add(lastMatchButton, "skip,wrap,span");

    return panel;
  }


  private JPanel createDecksUi() {
    JPanel panel = new JPanel();

    MigLayout layout = new MigLayout();
    panel.setLayout(layout);

    panel.add(new JLabel(" "), "wrap");
    panel.add(new JLabel(t("set_your_deck_slots")), "skip,span,wrap");
    panel.add(new JLabel(" "), "wrap");

    panel.add(new JLabel(t("deck_slot.label_1")), "skip");
    panel.add(new JLabel(t("deck_slot.label_2")), "");
    panel.add(new JLabel(t("deck_slot.label_3")), "wrap");

    deckSlot1Field = new JComboBox<>();
    panel.add(deckSlot1Field, "skip");
    deckSlot2Field = new JComboBox<>();
    panel.add(deckSlot2Field, "");
    deckSlot3Field = new JComboBox<>();
    panel.add(deckSlot3Field, "wrap");

    panel.add(new JLabel(" "), "wrap");

    panel.add(new JLabel(t("deck_slot.label_4")), "skip");
    panel.add(new JLabel(t("deck_slot.label_5")), "");
    panel.add(new JLabel(t("deck_slot.label_6")), "wrap");

    deckSlot4Field = new JComboBox<>();
    panel.add(deckSlot4Field, "skip");
    deckSlot5Field = new JComboBox<>();
    panel.add(deckSlot5Field, "");
    deckSlot6Field = new JComboBox<>();
    panel.add(deckSlot6Field, "wrap");

    panel.add(new JLabel(" "), "wrap");

    panel.add(new JLabel(t("deck_slot.label_7")), "skip");
    panel.add(new JLabel(t("deck_slot.label_8")), "");
    panel.add(new JLabel(t("deck_slot.label_9")), "wrap");

    deckSlot7Field = new JComboBox<>();
    panel.add(deckSlot7Field, "skip");
    deckSlot8Field = new JComboBox<>();
    panel.add(deckSlot8Field, "");
    deckSlot9Field = new JComboBox<>();
    panel.add(deckSlot9Field, "wrap");

    panel.add(new JLabel(" "), "wrap");
    panel.add(new JLabel(" "), "wrap");

    JButton saveButton = new JButton(t("button.save_deck_slots"));
    saveButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        saveDeckSlots();
      }
    });
    panel.add(saveButton, "skip");

    JButton refreshButton = new JButton(t("button.refresh"));
    refreshButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          updateDecksTab();
        } catch (IOException e1) {
          Main.showErrorDialog("Error updating decks", e1);
        }
      }
    });
    panel.add(refreshButton, "wrap,span");

    panel.add(new JLabel(" "), "wrap");
    panel.add(new JLabel(" "), "wrap");

    JButton myDecksButton = new JButton(t("manage_decks_on_hsnet"));
    myDecksButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          Desktop.getDesktop().browse(new URI(DECKS_URL));
        } catch (Throwable e1) {
          Main.showErrorDialog("Error launching browser with URL" + DECKS_URL, e1);
        }
      }
    });
    panel.add(myDecksButton, "skip,span");

    return panel;
  }


  private JPanel createOptionsUi() {
    JPanel panel = new JPanel();

    MigLayout layout = new MigLayout();
    panel.setLayout(layout);

    panel.add(new JLabel(" "), "wrap");

    // user key
    panel.add(new JLabel(t("options.label.userkey") + " "), "skip,right");
    userKeyField = new JTextField();
    userKeyField.setText(ConfigDeprecated.getUserKey());
    panel.add(userKeyField, "wrap");

    // monitoring method
    panel.add(new JLabel(t("options.label.monitoring")), "skip,right");
    monitoringMethodField = new JComboBox<>(new String[]{t("options.label.monitoring.screen"), t("options.label.monitoring.log")});
    monitoringMethodField.setSelectedIndex(ConfigDeprecated.monitoringMethod().ordinal());
    panel.add(monitoringMethodField, "");

    HelpIcon monitoringHelpIcon = new HelpIcon("https://github.com/HearthStats/HearthStats.net-Uploader/wiki/Options:-Monitoring", "Help on monitoring options");
    panel.add(monitoringHelpIcon, "wrap");


    // check for updates
    panel.add(new JLabel(t("options.label.updates") + " "), "skip,right");
    checkUpdatesField = new JCheckBox(t("options.check_updates"));
    checkUpdatesField.setSelected(ConfigDeprecated.checkForUpdates());
    panel.add(checkUpdatesField, "wrap");

    // show notifications
    panel.add(new JLabel(t("options.label.notifications") + " "), "skip,right");
    notificationsEnabledField = new JCheckBox("Show notifications");
    notificationsEnabledField.setSelected(ConfigDeprecated.showNotifications());
    notificationsEnabledField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateNotificationCheckboxes();
      }
    });
    panel.add(notificationsEnabledField, "wrap");


    // When running on Mac OS X 10.8 or later, the format of the notifications can be changed
    if (ConfigDeprecated.isOsxNotificationsSupported()) {
      panel.add(new JLabel(""), "skip,right");
      JLabel notificationsFormatLabel = new JLabel(t("options.label.notifyformat.label"));
      panel.add(notificationsFormatLabel, "split 2, gapleft 27");
      notificationsFormat = new JComboBox<>(new String[]{t("options.label.notifyformat.osx"), t("options.label.notifyformat.hearthstats")});
      notificationsFormat.setSelectedIndex(ConfigDeprecated.useOsxNotifications() ? 0 : 1);
      panel.add(notificationsFormat, "");

      HelpIcon osxNotificationsHelpIcon = new HelpIcon("https://github.com/HearthStats/HearthStats.net-Uploader/wiki/Options:-OS-X-Notifications", "Help on notification style options");
      panel.add(osxNotificationsHelpIcon, "wrap");
    }

    // show HS found notification
    panel.add(new JLabel(""), "skip,right");
    showHsFoundField = new JCheckBox(t("options.notification.hs_found"));
    showHsFoundField.setSelected(ConfigDeprecated.showHsFoundNotification());
    panel.add(showHsFoundField, "wrap");

    // show HS closed notification
    panel.add(new JLabel(""), "skip,right");
    showHsClosedField = new JCheckBox(t("options.notification.hs_closed"));
    showHsClosedField.setSelected(ConfigDeprecated.showHsClosedNotification());
    panel.add(showHsClosedField, "wrap");

    // show game screen notification
    panel.add(new JLabel(""), "skip,right");
    showScreenNotificationField = new JCheckBox(t("options.notification.screen"));
    showScreenNotificationField.setSelected(ConfigDeprecated.showScreenNotification());
    panel.add(showScreenNotificationField, "wrap");

    // show game mode notification
    panel.add(new JLabel(""), "skip,right");
    showModeNotificationField = new JCheckBox(t("options.notification.mode"));
    showModeNotificationField.setSelected(ConfigDeprecated.showModeNotification());
    panel.add(showModeNotificationField, "wrap");

    // show deck notification
    panel.add(new JLabel(""), "skip,right");
    showDeckNotificationField = new JCheckBox(t("options.notification.deck"));
    showDeckNotificationField.setSelected(ConfigDeprecated.showDeckNotification());
    panel.add(showDeckNotificationField, "wrap");

    // show your turn notification
    panel.add(new JLabel(""), "skip,right");
    showYourTurnNotificationField = new JCheckBox(t("options.notification.turn"));
    showYourTurnNotificationField.setSelected(ConfigDeprecated.showYourTurnNotification());
    panel.add(showYourTurnNotificationField, "wrap");

    updateNotificationCheckboxes();

    // show deck overlay
    panel.add(new JLabel(""), "skip,right");
    showDeckOverlay = new JCheckBox(t("options.ui.deckOverlay"));
    showDeckOverlay.setSelected(ConfigDeprecated.showDeckOverlay());
    panel.add(showDeckOverlay, "");

    HelpIcon deckOverlayHelpIcon = new HelpIcon("https://github.com/HearthStats/HearthStats.net-Uploader/wiki/Options:-Deck-Overlay", "Help on the show deck overlay option");
    panel.add(deckOverlayHelpIcon, "wrap");

    // match popup
    panel.add(new JLabel(t("options.label.matchpopup")), "skip,right");

    showMatchPopupField = new JComboBox<>(new String[]{t("options.label.matchpopup.always"), t("options.label.matchpopup.incomplete"), t("options.label.matchpopup.never")});
    showMatchPopupField.setSelectedIndex(ConfigDeprecated.showMatchPopup().ordinal());
    panel.add(showMatchPopupField, "");

    HelpIcon matchPopupHelpIcon = new HelpIcon("https://github.com/HearthStats/HearthStats.net-Uploader/wiki/Options:-Match-Popup", "Help on the match popup options");
    panel.add(matchPopupHelpIcon, "wrap");


    // minimize to tray
    panel.add(new JLabel("Interface: "), "skip,right");
    minToTrayField = new JCheckBox(t("options.notification.min_to_tray"));
    minToTrayField.setSelected(ConfigDeprecated.checkForUpdates());
    panel.add(minToTrayField, "wrap");

    // start minimized
    panel.add(new JLabel(""), "skip,right");
    startMinimizedField = new JCheckBox(t("options.notification.start_min"));
    startMinimizedField.setSelected(ConfigDeprecated.startMinimized());
    panel.add(startMinimizedField, "wrap");

    // analytics
    panel.add(new JLabel("Analytics: "), "skip,right");
    analyticsField = new JCheckBox(t("options.submit_stats"));

    final Monitor frame = this;
    analyticsField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!analyticsField.isSelected()) {
          int dialogResult = JOptionPane.showConfirmDialog(frame,
            "A lot of work has gone into this uploader.\n" +
              "It is provided for free, and all we ask in return\n" +
              "is that you let us track basic, anonymous statistics\n" +
              "about how frequently it is being used." +
              "\n\nAre you sure you want to disable analytics?"
            ,
            "Please reconsider ...",
            JOptionPane.YES_NO_OPTION);
          if (dialogResult == JOptionPane.NO_OPTION) {
            analyticsField.setSelected(true);
          }
        }
      }
    });
    analyticsField.setSelected(ConfigDeprecated.analyticsEnabled());
    panel.add(analyticsField, "wrap");

    // Save button
    panel.add(new JLabel(""), "skip,right");
    JButton saveOptionsButton = new JButton(t("button.save_options"));
    saveOptionsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        saveOptions();
      }
    });
    panel.add(saveOptionsButton, "wrap");


    return panel;
  }


  private void updateNotificationCheckboxes() {
    boolean isEnabled = notificationsEnabledField.isSelected();
    if (notificationsFormat != null) {
      notificationsFormat.setEnabled(isEnabled);
    }
    showHsFoundField.setEnabled(isEnabled);
    showHsClosedField.setEnabled(isEnabled);
    showScreenNotificationField.setEnabled(isEnabled);
    showModeNotificationField.setEnabled(isEnabled);
    showDeckNotificationField.setEnabled(isEnabled);
  }


  private String name(JSONObject o) {
    return hsClassOptions[Integer.parseInt(o.get("klass_id").toString())]
      + " - " + o.get("name").toString().toLowerCase();
  }


  private void applyDecksToSelector(JComboBox<String> selector, Integer slotNum) {

    selector.setMaximumSize(new Dimension(200, selector.getSize().height));
    selector.removeAllItems();

    selector.addItem("- Select a deck -");

    List<JSONObject> decks = DeckUtils.getDecks();

    Collections.sort(decks, new Comparator<JSONObject>() {
      @Override
      public int compare(JSONObject o1, JSONObject o2) {
        return name(o1).compareTo(name(o2));
      }

    });

    for (int i = 0; i < decks.size(); i++) {
      selector.addItem(name(decks.get(i))
        + "                                       #"
        + decks.get(i).get("id"));
      if (decks.get(i).get("slot") != null && decks.get(i).get("slot").toString().equals(slotNum.toString()))
        selector.setSelectedIndex(i + 1);
    }
  }


  private void updateDecksTab() throws IOException {
    DeckUtils.updateDecks();
    applyDecksToSelector(deckSlot1Field, 1);
    applyDecksToSelector(deckSlot2Field, 2);
    applyDecksToSelector(deckSlot3Field, 3);
    applyDecksToSelector(deckSlot4Field, 4);
    applyDecksToSelector(deckSlot5Field, 5);
    applyDecksToSelector(deckSlot6Field, 6);
    applyDecksToSelector(deckSlot7Field, 7);
    applyDecksToSelector(deckSlot8Field, 8);
    applyDecksToSelector(deckSlot9Field, 9);
  }


  private void checkForUpdates() {
    if (ConfigDeprecated.checkForUpdates()) {
      Log.info(t("checking_for_updates..."));
      try {
        String availableVersion = Updater.getAvailableVersion();
        if (availableVersion != null) {
          Log.info(t("latest_v_available") + " " + availableVersion);

          if (!availableVersion.matches(Application.version())) {

            bringWindowToFront();

            int dialogButton = JOptionPane.YES_NO_OPTION;
            int dialogResult = JOptionPane.showConfirmDialog(this,
              "A new version of this uploader is available\n\n" +
                Updater.getRecentChanges() +
                "\n\n" + t("would_u_like_to_install_update")
              ,
              "HearthStats.net " + t("uploader_updates_avail"),
              dialogButton);
            if (dialogResult == JOptionPane.YES_OPTION) {
              Updater.run();
            } else {
              dialogResult = JOptionPane.showConfirmDialog(null,
                t("would_you_like_to_disable_updates"),
                t("disable_update_checking"),
                dialogButton);
              if (dialogResult == JOptionPane.YES_OPTION) {
                String[] options = {t("button.ok")};
                JPanel panel = new JPanel();
                JLabel lbl = new JLabel(t("reenable_updates_any_time"));
                panel.add(lbl);
                JOptionPane.showOptionDialog(this, panel, t("updates_disabled_msg"), JOptionPane.NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                ConfigDeprecated.setCheckForUpdates(false);
              }
            }
          }
        } else {
          Log.warn("Unable to determine latest available version");
        }
      } catch (Throwable e) {
        e.printStackTrace(System.err);
        notify("Update Checking Error", "Unable to determine the latest available version");
      }
    }
  }


  /**
   * Sets up the Hearthstone log monitoring if enabled, or stops if it is disabled
   */
  private void setupLogMonitoring() {
    setMonitorHearthstoneLog(ConfigDeprecated.monitoringMethod() == ConfigDeprecated.MonitoringMethod.SCREEN_LOG);
  }


  protected void notify(String header) {
    notify(header, "");
  }


  protected void notify(String header, String message) {
    if (ConfigDeprecated.showNotifications())
      notificationQueue.add(header, message, false);
  }


  protected void updateTitle() {
    String title = "HearthStats.net Uploader";
    if (hearthstoneDetected) {
      if (analyzer.getScreen() != null) {
        title += " - " + analyzer.getScreen().title;
        if (analyzer.getScreen() == Screen.PLAY_LOBBY && analyzer.getMode() != null) {
          title += " " + analyzer.getMode();
        }
        if (analyzer.getScreen() == Screen.FINDING_OPPONENT) {
          if (analyzer.getMode() != null) {
            title += " for " + analyzer.getMode() + " Game";
          }
        }

        // TODO: replace with enum values
        if ("Match Start".equals(analyzer.getScreen().title) || "Playing".equals(analyzer.getScreen().title)) {
          title += " " + (analyzer.getMode() == null ? "[undetected]" : analyzer.getMode());
          title += " " + (analyzer.getCoin() ? "" : "No ") + "Coin";
          title += " " + (analyzer.getYourClass() == null ? "[undetected]" : analyzer.getYourClass());
          title += " VS. " + (analyzer.getOpponentClass() == null ? "[undetected]" : analyzer.getOpponentClass());
        }
      }
    } else {
      title += " - Waiting for Hearthstone ";
    }
    setTitle(title);
  }


  private int getClassOptionIndex(String cName) {
    for (int i = 0; i < hsClassOptions.length; i++) {
      if (hsClassOptions[i].equals(cName)) {
        return i;
      }
    }
    return 0;
  }


  private void updateCurrentMatchUi() {
    HearthstoneMatch match = analyzer.getMatch();
    updateMatchClassSelectorsIfSet(match);
    if (currentMatchEnabled) {
      currentMatchLabel.setText(match.mode() + " Match - " + " Turn " + match.numTurns());
    } else {
      currentMatchLabel.setText("Waiting for next match to start ...");
    }
    currentOpponentNameField.setText(match.opponentName());

    currentOpponentClassSelect.setSelectedIndex(getClassOptionIndex(match.opponentClass()));
    currentYourClassSelector.setSelectedIndex(getClassOptionIndex(match.userClass()));

    currentGameCoinField.setSelected(match.coin());
    currentNotesField.setText(match.notes());
    // last match
    if (lastMatch != null && lastMatch.mode() != null) {
      if (lastMatch.result() != null) {
        String tooltip = (lastMatch.mode().equals("Arena") ? "View current arena run on" : "Edit the previous match")
          + " on HearthStats.net";
        lastMatchButton.setToolTipText(tooltip);
        lastMatchButton.setText(lastMatch.toString());
        lastMatchButton.setEnabled(true);
      }
    }
  }


  private void updateImageFrame() {
    if (!_drawPaneAdded) {
      add(drawPane);
    }
    if (image.getWidth() >= 1024) {
      setSize(image.getWidth(), image.getHeight());
    }
    drawPane.repaint();
    invalidate();
    validate();
    repaint();
  }


  private void submitMatchResult(HearthstoneMatch hsMatch) throws IOException {
    // check for new arena run
    if ("Arena".equals(hsMatch.mode()) && analyzer.isNewArena()) {
      ArenaRun run = new ArenaRun();
      run.setUserClass(hsMatch.userClass());
      Log.info("Creating new " + run.getUserClass() + "arena run");
      notify("Creating new " + run.getUserClass() + "arena run");
      api.createArenaRun(run);
      analyzer.setIsNewArena(false);
    }

    String header = "Submitting match result";
    String message = hsMatch.toString();
    notify(header, message);
    Log.matchResult(header + ": " + message);

    if (ConfigDeprecated.analyticsEnabled()) {
      analytics.trackEvent("app", "Submit" + hsMatch.mode() + "Match");
    }

    api.createMatch(hsMatch);
  }


  private void resetMatchClassSelectors() {
    currentYourClassSelector.setSelectedIndex(0);
    currentOpponentClassSelect.setSelectedIndex(0);
  }


  private void updateMatchClassSelectorsIfSet(HearthstoneMatch hsMatch) {
    if (currentYourClassSelector.getSelectedIndex() > 0) {
      hsMatch.userClass_$eq(hsClassOptions[currentYourClassSelector.getSelectedIndex()]);
    }
    if (currentOpponentClassSelect.getSelectedIndex() > 0) {
      hsMatch.opponentClass_$eq(hsClassOptions[currentOpponentClassSelect.getSelectedIndex()]);
    }
  }


  protected void handleHearthstoneFound() {
    // mark hearthstone found if necessary
    if (!hearthstoneDetected) {
      hearthstoneDetected = true;
      debugLog.debug("  - hearthstoneDetected");
      if (ConfigDeprecated.showHsFoundNotification()) {
        notify("Hearthstone found");
      }
      if (hearthstoneLogMonitor == null) {
        hearthstoneLogMonitor = new HearthstoneLogMonitor();
      }
      setupLogMonitoring();
    }

    // grab the image from Hearthstone
    debugLog.debug("  - screen capture");
    image = hsHelper.getScreenCapture();

    if (image == null) {
      debugLog.debug("  - screen capture returned null");
    } else {
      // detect image stats
      if (image.getWidth() >= 1024) {
        debugLog.debug("  - analysing image");
        analyzer.analyze(image);
      }

      if (ConfigDeprecated.mirrorGameImage()) {
        debugLog.debug("  - mirroring image");
        updateImageFrame();
      }
    }
  }


  protected void handleHearthstoneNotFound() {

    // mark hearthstone not found if necessary
    if (hearthstoneDetected) {
      hearthstoneDetected = false;
      debugLog.debug("  - changed hearthstoneDetected to false");
      if (ConfigDeprecated.showHsClosedNotification()) {
        notify("Hearthstone closed");
        analyzer.reset();
      }
    }
  }


  private void pollHsImpl() {
    boolean error = false;
    while (!error) {
      try {
        if (hsHelper.foundProgram()) {
          handleHearthstoneFound();
        } else {
          debugLog.debug("  - did not find Hearthstone");
          handleHearthstoneNotFound();
        }
        updateTitle();
        Thread.sleep(POLLING_INTERVAL_IN_MS);
      } catch (Throwable ex) {
        ex.printStackTrace(System.err);
        debugLog.error("  - exception which is not being handled:", ex);
        while (ex.getCause() != null) {
          ex = ex.getCause();
        }
        Log.error("ERROR: " + ex.getMessage() + ". You will need to restart HearthStats.net Uploader.", ex);
        error = true;
      } finally {
        debugLog.debug("<-- finished");
      }

    }
  }


  /**
   * Checks whether the match result is complete, showing a popup if necessary to fix the match data,
   * and then submits the match when ready.
   *
   * @param match The match to check and submit.
   */
  private void checkMatchResult(final HearthstoneMatch match) {

    updateMatchClassSelectorsIfSet(match);

    final ConfigDeprecated.MatchPopup matchPopup = ConfigDeprecated.showMatchPopup();
    final boolean showPopup;

    switch (matchPopup) {
      case ALWAYS:
        showPopup = true;
        break;
      case INCOMPLETE:
        showPopup = !match.isDataComplete();
        break;
      case NEVER:
        showPopup = false;
        break;
      default:
        throw new UnsupportedOperationException("Unknown config option " + ConfigDeprecated.showMatchPopup());
    }

    if (showPopup) {
      // Show a popup allowing the user to edit their match before submitting
      final Monitor monitor = this;
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          try {
            boolean matchHasValidationErrors = !match.isDataComplete();
            String infoMessage = null;
            do {
              if (infoMessage == null) {
                infoMessage = (matchPopup == ConfigDeprecated.MatchPopup.INCOMPLETE)
                  ? t("match.popup.message.incomplete")
                  : t("match.popup.message.always");
              }
              bringWindowToFront();
              MatchEndPopup.Button buttonPressed = MatchEndPopup.showPopup(monitor, match, infoMessage, t("match.popup.title"));
              matchHasValidationErrors = !match.isDataComplete();
              switch (buttonPressed) {
                case SUBMIT:
                  if (matchHasValidationErrors) {
                    infoMessage = "Some match information is incomplete.<br>Please update these details then click Submit to submit the match to HearthStats:";
                  } else {
                    submitMatchResult(match);
                  }
                  break;
                case CANCEL:
                  return;
              }

            } while (matchHasValidationErrors);
          } catch (IOException e) {
            Main.showErrorDialog("Error submitting match result", e);
          }
        }
      });

    } else {
      // Don't show a popup, submit the match directly
      try {
        submitMatchResult(match);
      } catch (IOException e) {
        Main.showErrorDialog("Error submitting match result", e);
      }
    }
  }


  private void handleAnalyserEvent(AnalyserEvent changed) throws IOException {
    switch (changed) {
      case ARENA_END:
        notify("End of Arena Run Detected");
        Log.info("End of Arena Run Detected");
        api.endCurrentArenaRun();
        break;

      case COIN:
        notify("Coin Detected");
        Log.info("Coin Detected");
        break;

      case DECK_SLOT:
        Deck deck = DeckUtils.getDeckFromSlot(analyzer.getDeckSlot());
        if (deck == null) {
          tabbedPane.setSelectedIndex(2);
          bringWindowToFront();
          Main.showMessageDialog(this, "Unable to determine what deck you have in slot #" + analyzer.getDeckSlot() + "\n\nPlease set your decks in the \"Decks\" tab.");
        } else {
          notify("Deck Detected", deck.name());
          Log.info("Deck Detected: " + deck.name() + " Detected");
        }

        break;

      case MODE:
        playingInMatch = false;
        setCurrentMatchEnabled(false);
        if (ConfigDeprecated.showModeNotification()) {
          debugLog.debug(analyzer.getMode() + " level " + analyzer.getRankLevel());
          if ("Ranked".equals(analyzer.getMode())) {
            notify(analyzer.getMode() + " Mode Detected", "Rank Level " + analyzer.getRankLevel());
          } else {
            notify(analyzer.getMode() + " Mode Detected");
          }
        }
        if ("Ranked".equals(analyzer.getMode())) {
          Log.info(analyzer.getMode() + " Mode Detected - Level " + analyzer.getRankLevel());
        } else {
          Log.info(analyzer.getMode() + " Mode Detected");
        }
        break;

      case NEW_ARENA:
        if (analyzer.isNewArena())
          notify("New Arena Run Detected");
        Log.info("New Arena Run Detected");
        break;

      case OPPONENT_CLASS:
        notify("Playing vs " + analyzer.getOpponentClass());
        Log.info("Playing vs " + analyzer.getOpponentClass());
        break;

      case OPPONENT_NAME:
        notify("Opponent: " + analyzer.getOpponentName());
        Log.info("Opponent: " + analyzer.getOpponentName());
        break;

      case RESULT:
        playingInMatch = false;
        setCurrentMatchEnabled(false);
        notify(analyzer.getResult() + " Detected");
        Log.info(analyzer.getResult() + " Detected");
        checkMatchResult(analyzer.getMatch());
        break;

      case SCREEN:

        boolean inGameModeScreen = (analyzer.getScreen() == Screen.ARENA_LOBBY || analyzer.getScreen() == Screen.ARENA_END || analyzer.getScreen() == Screen.PLAY_LOBBY);
        if (inGameModeScreen) {
          if (playingInMatch && analyzer.getResult() == null) {
            playingInMatch = false;
            notify("Detection Error", "Match result was not detected.");
            Log.info("Detection Error: Match result was not detected.");
            checkMatchResult(analyzer.getMatch());
          }
          playingInMatch = false;
        }

        if (analyzer.getScreen() == Screen.FINDING_OPPONENT) {
          // Ensure that log monitoring is running before starting the match because Hearthstone may only have created the log file
          // after the HearthStats Uploader started up. In that case log monitoring won't yet be running.
          setupLogMonitoring();
          resetMatchClassSelectors();
          //TODO : also display the overlay for Practice mode (usefull for tests)
          if (ConfigDeprecated.showDeckOverlay() && !"Arena".equals(analyzer.getMode())) {
            Deck selectedDeck = DeckUtils.getDeckFromSlot(analyzer.getDeckSlot());
            if (selectedDeck != null && selectedDeck.isValid() && hearthstoneLogMonitor != null) {
              ClickableDeckBox.showBox(selectedDeck, hearthstoneLogMonitor.cardEvents());
            } else {
              String message;
              if (selectedDeck == null) {
                message = "Invalid or empty deck, edit it on HearthStats.net to display deck overlay (you will need to restart the uploader)";
              } else {
                message = String.format("Invalid or empty deck, <a href='http://hearthstats.net/decks/%s/edit'>edit it on HearthStats.net</a> to display deck overlay (you will need to restart the uploader)", selectedDeck.slug());
              }
              notify(message);
              Log.info(message);
            }
          }
        }

        if (analyzer.getScreen().group == ScreenGroup.MATCH_START) {
          setCurrentMatchEnabled(true);
          playingInMatch = true;
        }

        if (analyzer.getScreen().group != ScreenGroup.MATCH_END && !DO_NOT_NOTIFY_SCREENS.contains(analyzer.getScreen())
          && ConfigDeprecated.showScreenNotification()) {
          if (analyzer.getScreen() == Screen.PRACTICE_LOBBY) {
            notify(analyzer.getScreen().title + " Screen Detected", "Results are not tracked in practice mode");
          } else {
            notify(analyzer.getScreen().title + " Screen Detected");
          }
        }

        if (analyzer.getScreen() == Screen.PRACTICE_LOBBY) {
          Log.info(analyzer.getScreen().title + " Screen Detected. Result tracking disabled.");
        } else {
          if (analyzer.getScreen() == Screen.MATCH_VS) {
            Log.divider();
          }
          Log.info(analyzer.getScreen().title + " Screen Detected");
        }
        break;

      case YOUR_CLASS:
        notify("Playing as " + analyzer.getYourClass());
        Log.info("Playing as " + analyzer.getYourClass());
        break;

      case YOUR_TURN:
        if (ConfigDeprecated.showYourTurnNotification()) {
          notify((analyzer.isYourTurn() ? "Your" : "Opponent") + " turn detected");
        }
        Log.info((analyzer.isYourTurn() ? "Your" : "Opponent") + " turn detected");
        break;

      case ERROR_ANALYSING_IMAGE:
        notify("Error analysing opponent name image");
        Log.info("Error analysing opponent name image");
        break;

      default:
        notify("Unhandled event");
        Log.info("Unhandled event");
    }
    updateCurrentMatchUi();
  }


  public LogPane getLogPane() {
    return logText;
  }


  private void handleApiEvent(Object changed) {
    switch (changed.toString()) {
      case "error":
        notify("API Error", api.getMessage());
        Log.error("API Error: " + api.getMessage());
        Main.showMessageDialog(this, "API Error: " + api.getMessage());
        break;
      case "result":
        Log.info("API Result: " + api.getMessage());
        lastMatch = analyzer.getMatch();
        lastMatch.id_$eq(api.getLastMatchId());
        setCurrentMatchEnabled(false);
        updateCurrentMatchUi();
        // new line after match result
        if (api.getMessage().matches(".*(Edit match|Arena match successfully created).*")) {
          analyzer.resetMatch();
          resetMatchClassSelectors();
          Log.divider();
        }
        break;
    }
  }


  private void handleProgramHelperEvent(Object changed) {
    Log.info(changed.toString());
    if (changed.toString().matches(".*minimized.*")) {
      notify("Hearthstone Minimized", "Warning! No detection possible while minimized.");
    }
    if (changed.toString().matches(".*fullscreen.*")) {
      JOptionPane.showMessageDialog(this, "Hearthstats.net Uploader Warning! \n\nNo detection possible while Hearthstone is in fullscreen mode.\n\nPlease set Hearthstone to WINDOWED mode and close and RESTART Hearthstone.\n\nSorry for the inconvenience.");
    }
    if (changed.toString().matches(".*restored.*")) {
      notify("Hearthstone Restored", "Resuming detection ...");
    }
  }


  @Override
  public void update(Observable dispatcher, Object changed) {
    if (dispatcher.getClass().isAssignableFrom(HearthstoneAnalyser.class))
      try {
        handleAnalyserEvent((AnalyserEvent) changed);
      } catch (IOException e) {
        Main.showErrorDialog("Error handling analyzer event", e);
      }
    if (dispatcher.getClass().isAssignableFrom(API.class)) {
      handleApiEvent(changed);
    }

    if (dispatcher.getClass().toString().matches(".*ProgramHelper(Windows|Osx)?")) {
      handleProgramHelperEvent(changed);
    }
  }


  private Integer getDeckSlotDeckId(JComboBox selector) {
    Integer deckId = null;
    String deckStr = (String) selector.getItemAt(selector.getSelectedIndex());
    Pattern pattern = Pattern.compile("[^0-9]+([0-9]+)$");
    Matcher matcher = pattern.matcher(deckStr);
    if (matcher.find()) {
      deckId = Integer.parseInt(matcher.group(1));
    }
    return deckId;
  }


  private void saveDeckSlots() {

    try {
      api.setDeckSlots(
        getDeckSlotDeckId(deckSlot1Field),
        getDeckSlotDeckId(deckSlot2Field),
        getDeckSlotDeckId(deckSlot3Field),
        getDeckSlotDeckId(deckSlot4Field),
        getDeckSlotDeckId(deckSlot5Field),
        getDeckSlotDeckId(deckSlot6Field),
        getDeckSlotDeckId(deckSlot7Field),
        getDeckSlotDeckId(deckSlot8Field),
        getDeckSlotDeckId(deckSlot9Field)
      );
      Main.showMessageDialog(this, api.getMessage());
      updateDecksTab();
    } catch (Throwable e) {
      Main.showErrorDialog("Error saving deck slots", e);
    }
  }


  private void saveOptions() {
    debugLog.debug("Saving options...");

    ConfigDeprecated.MonitoringMethod monitoringMethod = ConfigDeprecated.MonitoringMethod.values()[monitoringMethodField.getSelectedIndex()];

    ConfigDeprecated.setUserKey(userKeyField.getText());
    ConfigDeprecated.setMonitoringMethod(monitoringMethod);
    ConfigDeprecated.setCheckForUpdates(checkUpdatesField.isSelected());
    ConfigDeprecated.setShowNotifications(notificationsEnabledField.isSelected());
    ConfigDeprecated.setShowHsFoundNotification(showHsFoundField.isSelected());
    ConfigDeprecated.setShowHsClosedNotification(showHsClosedField.isSelected());
    ConfigDeprecated.setShowScreenNotification(showScreenNotificationField.isSelected());
    ConfigDeprecated.setShowModeNotification(showModeNotificationField.isSelected());
    ConfigDeprecated.setShowDeckNotification(showDeckNotificationField.isSelected());
    ConfigDeprecated.setShowYourTurnNotification(showYourTurnNotificationField.isSelected());
    ConfigDeprecated.setShowDeckOverlay(showDeckOverlay.isSelected());
    ConfigDeprecated.setShowMatchPopup(ConfigDeprecated.MatchPopup.values()[showMatchPopupField.getSelectedIndex()]);
    ConfigDeprecated.setAnalyticsEnabled(analyticsField.isSelected());
    ConfigDeprecated.setMinToTray(minToTrayField.isSelected());
    ConfigDeprecated.setStartMinimized(startMinimizedField.isSelected());

    if (notificationsFormat != null) {
      // This control only appears on OS X machines, will be null on Windows machines
      ConfigDeprecated.setUseOsxNotifications(notificationsFormat.getSelectedIndex() == 0);
      notificationQueue = environment.newNotificationQueue();
    }

    setupLogMonitoring();

    try {
      ConfigDeprecated.save();
      debugLog.debug("...save complete");
      JOptionPane.showMessageDialog(this, "Options Saved");
    } catch (Throwable e) {
      Log.warn("Error occurred trying to write settings file, your settings may not be saved", e);
      JOptionPane.showMessageDialog(null, "Error occurred trying to write settings file, your settings may not be saved");
    }
  }


  private void setCurrentMatchEnabled(Boolean enabled) {
    currentMatchEnabled = enabled;
    currentYourClassSelector.setEnabled(enabled);
    currentOpponentClassSelect.setEnabled(enabled);
    currentGameCoinField.setEnabled(enabled);
    currentOpponentNameField.setEnabled(enabled);
    currentNotesField.setEnabled(enabled);
  }


  private void enableMinimizeToTray() {
    if (SystemTray.isSupported()) {

      tray = SystemTray.getSystemTray();

      ActionListener exitListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          System.exit(0);
        }
      };
      PopupMenu popup = new PopupMenu();
      MenuItem defaultItem = new MenuItem("Restore");
      defaultItem.setFont(new Font("Arial", Font.BOLD, 14));
      defaultItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          setVisible(true);
          setExtendedState(JFrame.NORMAL);
        }
      });
      popup.add(defaultItem);
      defaultItem = new MenuItem("Exit");
      defaultItem.addActionListener(exitListener);
      defaultItem.setFont(new Font("Arial", Font.PLAIN, 14));
      popup.add(defaultItem);
      Image icon = new ImageIcon(getClass().getResource("/images/icon.png")).getImage();
      trayIcon = new TrayIcon(icon, "HearthStats.net Uploader", popup);
      trayIcon.setImageAutoSize(true);
      trayIcon.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          if (e.getClickCount() >= 2) {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
          }
        }
      });
    } else {
      debugLog.debug("system tray not supported");
    }
    addWindowStateListener(new WindowStateListener() {
      public void windowStateChanged(WindowEvent e) {
        if (ConfigDeprecated.minimizeToTray()) {
          if (e.getNewState() == ICONIFIED) {
            try {
              tray.add(trayIcon);
              setVisible(false);
            } catch (AWTException ex) {
            }
          }
          if (e.getNewState() == 7) {
            try {
              tray.add(trayIcon);
              setVisible(false);
            } catch (AWTException ex) {
            }
          }
          if (e.getNewState() == MAXIMIZED_BOTH) {
            tray.remove(trayIcon);
            setVisible(true);
          }
          if (e.getNewState() == NORMAL) {
            tray.remove(trayIcon);
            setVisible(true);
            debugLog.debug("Tray icon removed");
          }
        }
      }
    });

  }


  public void setMonitorHearthstoneLog(boolean monitorHearthstoneLog) {
    debugLog.debug("setMonitorHearthstoneLog({})", monitorHearthstoneLog);

    if (monitorHearthstoneLog) {
      // Ensure that the Hearthstone log.config file has been created
      Boolean configWasCreated = hsHelper.createConfig();

      // Start monitoring the Hearthstone log immediately if Hearthstone is already running
      if (hearthstoneDetected) {
        if (configWasCreated) {
          // Hearthstone won't actually be logging yet because the log.config was created after Hearthstone started up
          Log.help("Hearthstone log.config changed &mdash; please restart Hearthstone so that it starts generating logs");
        } else if (hearthstoneLogMonitor == null)
          hearthstoneLogMonitor = new HearthstoneLogMonitor();
      }
    } else {
      // Stop monitoring the Hearthstone log
      if (hearthstoneLogMonitor != null) {
        hearthstoneLogMonitor.stop();
        hearthstoneLogMonitor = null;
      }
    }
  }


}
