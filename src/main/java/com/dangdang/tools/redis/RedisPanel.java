package com.dangdang.tools.redis;

import com.dangdang.tools.redis.util.RedisUtil;
import com.dangdang.tools.redis.util.SwingUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;

/**
 * Created by wangshichun on 2015/12/9.
 */
public class RedisPanel extends JPanel {
    private JLabel redisServersLabel = new JLabel();
    private JComboBox<String> charsetBox = new JComboBox<String>();
    private JComboBox<String> dbBox = new JComboBox<String>();
    public RedisPanel() {
        super();
        this.setPreferredSize(SwingUtil.SCREEN_SIZE);
        this.setBackground(new Color(230, 230, 230));
//        this.setAutoscrolls(true);
        this.setLayout(new FlowLayout(FlowLayout.LEFT));
        this.add(redisServersLabel);
        this.add(new JLabel("强制db为:"));
        this.add(dbBox);
        for (int i = 0; i < 10; i++) {
            dbBox.addItem(String.valueOf(i));
        }
        SwingUtil.addNewLineTo(this);

        JLabel label = new JLabel("redis的key:");
        this.add(label);

        final JTextField redisKey = new JTextField();
        redisKey.setColumns(SwingUtil.DEFAULT_TEXT_FIELD_COLUMNS);
        redisKey.setComponentPopupMenu(SwingUtil.makePopupMenu());
        this.add(redisKey);

        label = new JLabel("value的字符集:");
        this.add(label);
        charsetBox.addItem("UTF8");
        charsetBox.addItem("GBK");
        this.add(charsetBox);

        SwingUtil.addNewLineTo(this);
        SwingUtil.addSpaceTo(this, 4);
        final JButton getButton = new JButton("查询(get)"); // 同时输出type、exists
        this.add(getButton);
        // get命令结果
        final JTextArea getResultTextArea = new JTextArea(3, SwingUtil.SCREEN_SIZE.width * 7 / 10 / SwingUtil.SPACE_WIDTH / 5);
        getResultTextArea.setLineWrap(true);
        this.add(new JScrollPane(getResultTextArea));
        getResultTextArea.setComponentPopupMenu(SwingUtil.makePopupMenu());
        // type命令结果
        final JLabel typeLabel = new JLabel();
        this.add(typeLabel);
        // exists命令结果
        final JLabel existsLabel = new JLabel();
        this.add(existsLabel);
        // exists命令结果
        final JLabel ttlLabel = new JLabel();
        this.add(ttlLabel);
        getButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                doGet(redisKey.getText().trim(), getResultTextArea, typeLabel, existsLabel, ttlLabel);
            }
        });

        SwingUtil.addNewLineTo(this, 1);
        SwingUtil.addSpaceTo(this, 4);
        JButton delButton = new JButton("删除(del)");
        this.add(delButton);
        final JLabel delResult = new JLabel();
        this.add(delResult);
        delButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                doDel(redisKey.getText().trim(), delResult, false);
            }
        });

        SwingUtil.addNewLineTo(this, 1);
        SwingUtil.addSpaceTo(this, 4);
        JButton setButton = new JButton("设置(set)"); // 同时设置TTL
        this.add(setButton);
        label = new JLabel("value设置为字符串:");
        this.add(label);
        final JTextArea redisValueTextArea = new JTextArea(7, SwingUtil.SCREEN_SIZE.width * 7 / 10 / SwingUtil.SPACE_WIDTH / 10);
        redisValueTextArea.setLineWrap(true);
        this.add(new JScrollPane(redisValueTextArea));
        redisValueTextArea.setComponentPopupMenu(SwingUtil.makePopupMenu());
        final JComboBox setFlagBox = new JComboBox();
        setFlagBox.addItem("");
        setFlagBox.addItem("nx:不存在时才设置");
        setFlagBox.addItem("xx:仅存在时才设置");
        this.add(setFlagBox);
        label = new JLabel("TTL(秒):");
        this.add(label);
        final JTextField ttlTextField = new JTextField(5);
        this.add(ttlTextField);
        final JLabel setResult = new JLabel();
        this.add(setResult);
        setButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String result = doSet(redisKey.getText().trim(), redisValueTextArea.getText().trim(),
                        setFlagBox.getSelectedItem().toString(), ttlTextField.getText().trim());
                setResult.setText("set的结果:" + result);
            }
        });
        SwingUtil.addNewLineTo(this, 1);

        SwingUtil.addNewLineTo(this);
        SwingUtil.addSpaceTo(this, 4);
        JButton scanButton = new JButton("正则匹配key(最多只扫描shard中的前2000条)");
        this.add(scanButton);
        label = new JLabel("查哪个shard(从0开始):");
        this.add(label);
        final JTextField shardText = new JTextField(3);
        this.add(shardText);
        shardText.setText("0");
        shardText.setComponentPopupMenu(SwingUtil.makePopupMenu());
        label = new JLabel("正则(*或?或[1-9]等):");
        this.add(label);
        final JTextField scanPattern = new JTextField(SwingUtil.DEFAULT_TEXT_FIELD_COLUMNS);
        this.add(scanPattern);
        scanPattern.setText("*promise*");
        SwingUtil.addNewLineTo(this);
        SwingUtil.addSpaceTo(this, 4);
        final JList<String> scanResultList = new JList<String>();
        scanResultList.setVisibleRowCount(15);
        scanResultList.setDragEnabled(true);
        scanResultList.setModel(new DefaultListModel());
        JScrollPane scrollPane = new JScrollPane(scanResultList);
        this.add(scrollPane);
        final JLabel scanResultLabel = new JLabel();
        this.add(scanResultLabel);
        final JButton delSelectedButton = new JButton("删除所选");
        this.add(delSelectedButton);
        delSelectedButton.setVisible(false);
        scanButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                doScan(scanPattern.getText().trim(), shardText.getText().trim(), scanResultList, scanResultLabel);
                delSelectedButton.setVisible(scanResultList.getModel().getSize() > 0);
            }
        });
        scanResultList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                java.util.List<String> list = scanResultList.getSelectedValuesList();
                if (list.size() > 0 && !list.get(0).equals(redisKey.getText().trim())) {
                    redisKey.setText(list.get(0));
                    doGet(redisKey.getText().trim(), getResultTextArea, typeLabel, existsLabel, ttlLabel);
                }
            }
        });

        delSelectedButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                java.util.List<String> list = scanResultList.getSelectedValuesList();
                if (list.size() > 0) {
                    if (!SwingUtil.confirm("确定删除所选的 " + list.size() + " 条记录吗？"))
                        return;
                }
                for (String key : list) {
                    DefaultListModel<String> model = (DefaultListModel<String>) scanResultList.getModel();
                    doDel(key, delResult, true);
                    if ("OK".equals(delResult.getText())) {
                        model.removeElement(key);
                    }
                }
            }
        });
    }

    private void doScan(String keyPattern, String shardStr, JList<String> scanResultList, JLabel resultLabel) {
        DefaultListModel<String> listModel = new DefaultListModel<String>();
        if (keyPattern.isEmpty()) {
            SwingUtil.alert("请先填写正则表达式pattern");
            return;
        }
        if (!RedisUtil.testConnected()) {
            SwingUtil.alert("请点击“连接”按钮进行连接");
            return;
        }

        java.util.List<String> scanResult = RedisUtil.scan(keyPattern, shardStr, dbBox.getSelectedIndex());
        for (String key : scanResult) {
            listModel.addElement(key);
        }
        scanResultList.setModel(listModel);
        resultLabel.setText("共 " + scanResult.size() + " 条");
    }

    private void doDel(String key, JLabel delResult, boolean forceDelete) {
        delResult.setText("");
        if (key.isEmpty()) {
            SwingUtil.alert("请先填写key");
            delResult.setText("");
            return;
        }
        if (!RedisUtil.testConnected()) {
            SwingUtil.alert("请点击“连接”按钮进行连接");
            return;
        }
        if (forceDelete || SwingUtil.confirm("确认删除key“" + key + "”吗？")) {
            delResult.setText(RedisUtil.del(key, dbBox.getSelectedIndex()) ? "OK" : "Fail or not exist");
        }
    }

    private String doSet(String key, String value, String exists, String ttlValue) {
        String result = "";
        if (!RedisUtil.testConnected()) {
            SwingUtil.alert("请点击“连接”按钮进行连接");
            return result;
        }
        if (key.isEmpty()) {
            SwingUtil.alert("请先填写key");
            return result;
        }
        if (value.isEmpty()) {
            if (!SwingUtil.confirm("确定设置该key对应的value为空字符串吗？")) {
                return result;
            }
        }
        return RedisUtil.set(dbBox.getSelectedIndex(), key, value, ttlValue, exists);
    }

    private void doGet(String key, JTextComponent resultText, JLabel typeText, JLabel existsText, JLabel ttlLabel) {
        if (key == null || key.isEmpty()) {
            SwingUtil.alert("key不能为空");
            return;
        }

        if (!RedisUtil.testConnected()) {
            SwingUtil.alert("请点击“连接”按钮进行连接");
            return;
        }
        byte[] arr = RedisUtil.get(dbBox.getSelectedIndex(), key);
        if (null == arr || arr.length == 0) {
            resultText.setText("");
        } else {
            if ((arr[0] == 123 && arr[arr.length - 2] == 125 && arr[arr.length - 1] == 2) // {}
                    || (arr[0] == 91 && arr[arr.length - 2] == 93 && arr[arr.length - 1] == 2) // []
                    ) { // json
                resultText.setText(new String(arr, 0, arr.length - 1, Charset.forName(charsetBox.getSelectedItem().toString())));
            } else {
                resultText.setText(new String(arr, Charset.forName(charsetBox.getSelectedItem().toString())));
            }
        }

        typeText.setText("type:" + RedisUtil.type(dbBox.getSelectedIndex(), key));
        existsText.setText("exists:" + RedisUtil.exists(dbBox.getSelectedIndex(), key).toString());
        ttlLabel.setText("ttl(秒):" + RedisUtil.ttl(dbBox.getSelectedIndex(), key));
    }

    public void setShardedJedisInfo() {
        if (!RedisUtil.testConnected()) {
            redisServersLabel.setText("未连接");
            return;
        }

        redisServersLabel.setText(RedisUtil.getShardsInfoString());
    }
}
