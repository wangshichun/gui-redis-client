package com.dangdang.tools.redis.util;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;

/**
 * Created by wangshichun on 2015/12/9.
 */
public class SwingUtil {
    public static final int DEFAULT_TEXT_FIELD_COLUMNS = 30;
    public static final Dimension SCREEN_SIZE = Toolkit.getDefaultToolkit().getScreenSize();
    public static final int SPACE_WIDTH = new JLabel(" ").getPreferredSize().width;

    public static void addNewLineTo(Container container) {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(SCREEN_SIZE.width, 0));
        container.add(label);
    }
    public static void addNewLineTo(Container container, int lineHeight) {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(SCREEN_SIZE.width, lineHeight));
        label.setBorder(new LineBorder(Color.BLACK));
        container.add(label);
    }

    public static void addSpaceTo(Container container, int spaceSize) {
        JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(spaceSize * SPACE_WIDTH, 0));
        container.add(label);
    }

    public static JPopupMenu makePopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        DefaultEditorKit.CutAction cutAction = new DefaultEditorKit.CutAction();
        cutAction.putValue(Action.NAME, "剪切");
        DefaultEditorKit.CopyAction copyAction = new DefaultEditorKit.CopyAction();
        copyAction.putValue(Action.NAME, "复制");
        DefaultEditorKit.PasteAction pasteAction = new DefaultEditorKit.PasteAction();
        pasteAction.putValue(Action.NAME, "粘贴");

        menu.add(cutAction);
        menu.add(copyAction);
        menu.add(pasteAction);
        return menu;
    }

    public static void alert(String msg) {
        JOptionPane.showMessageDialog(null, msg);
    }

    public static boolean confirm(String msg) {
        return JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(null, msg);
    }
}
