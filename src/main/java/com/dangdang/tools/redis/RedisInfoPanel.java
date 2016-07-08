package com.dangdang.tools.redis;

import com.dangdang.tools.redis.util.RedisUtil;
import com.dangdang.tools.redis.util.SwingUtil;
import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisMonitor;
import redis.clients.util.Slowlog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wangshichun on 2016/7/8.
 */
@SuppressWarnings("ALL")
public class RedisInfoPanel extends JPanel {
    private JComboBox<String> nodesBox = new JComboBox<String>();
    private JComboBox<String> dbBox = new JComboBox<String>();
    private Runnable setInfoRun = null;

    public RedisInfoPanel() {
        super();
        this.setPreferredSize(SwingUtil.SCREEN_SIZE);
        this.setBackground(new Color(230, 230, 230));
        this.setLayout(new FlowLayout(FlowLayout.LEFT));
        this.add(nodesBox);
        nodesBox.setEditable(true);
        this.add(new JLabel("强制db为:"));
        this.add(dbBox);
        for (int i = 0; i < 16; i++) {
            dbBox.addItem(String.valueOf(i));
        }
        JButton refreshButton = new JButton("确定");
        this.add(refreshButton);
        refreshButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (setInfoRun != null)
                    setInfoRun.run();
            }
        });
        SwingUtil.addNewLineTo(this);


        final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
        tabbedPane.addTab("xxx", new JLabel("ddddddddddddd"));
        setInfoRun = new Runnable() {
            @Override
            public void run() {
                String address = (String) nodesBox.getSelectedItem();
                int db = Integer.valueOf((String) dbBox.getSelectedItem());
                displayInfo(address, db, tabbedPane);
            }
        };
        this.add(tabbedPane);
    }

    private String currentAddress = null;
    private void displayInfo(final String address, int db, JTabbedPane tabbedPane) {
        if (address == null || address.indexOf(":") < 0)
            return;

        Jedis jedis = RedisUtil.getJedis(address, db);
        if (jedis == null) {
            SwingUtil.alert("无法连接到：" + address);
            return;
        }

        tabbedPane.setVisible(false);
        int selectedIndex = tabbedPane.getSelectedIndex();
        tabbedPane.removeAll();
        currentAddress = address;

        // cluster info
        int columns = 100;
        int rows = 30;
        String info = RedisUtil.clusterInfoString(jedis);
        if (info != null && info.length() > 0) {
            JTextArea textArea = new JTextArea(info);
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            tabbedPane.addTab("cluster info集群状态", new JScrollPane(textArea));
        }
        info = RedisUtil.clusterNodesString(jedis);
        if (info != null && info.length() > 0) {
            JTextArea textArea = new JTextArea(info);
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            tabbedPane.addTab("cluster nodes集群节点", new JScrollPane(textArea));
        }
        info = RedisUtil.clusterSlotsString(jedis);
        if (info != null && info.length() > 0) {
            JTextArea textArea = new JTextArea(info);
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            tabbedPane.addTab("cluster slots集群slot分布", new JScrollPane(textArea));
        }
        List<String> tmpList = jedis.configGet("*");
        if (tmpList != null && tmpList.size() > 0) {
            JTextArea textArea = new JTextArea();
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < tmpList.size() / 2; i++) {
                builder.append(tmpList.get(i * 2)).append(":\t").append(tmpList.get(i * 2 + 1)).append("\n");
            }
            textArea.setText(builder.toString());
            tabbedPane.addTab("config get服务器配置", new JScrollPane(textArea));
        }

        info = jedis.info();
        if (info != null && info.length() > 0) {
            JTextArea textArea = new JTextArea(info);
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            tabbedPane.addTab("info实例状态", new JScrollPane(textArea));
        }
        Long time = jedis.lastsave();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (time != null && time > 0) {
            JTextArea textArea = new JTextArea(dateFormat.format(new Date(time * 1000)));
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            tabbedPane.addTab("lastsave最后落盘时间", new JScrollPane(textArea));
        }
        List<Slowlog> slowlogs = jedis.slowlogGet(100);
        if (slowlogs != null && slowlogs.size() > 0) {
            JTextArea textArea = new JTextArea();
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            StringBuilder builder = new StringBuilder();
            for (int i = slowlogs.size() - 1; i >= 0; i--) {
                Slowlog log = slowlogs.get(i);
                builder.append(log.getId()).append("\t").append(dateFormat.format(new Date(log.getTimeStamp() * 1000)))
                        .append("\t").append(log.getExecutionTime()).append(" ms: ").append(log.getArgs()).append("\n");
            }
            textArea.setText(builder.toString());
            tabbedPane.addTab("slowlog慢日志", new JScrollPane(textArea));
        }
        info = jedis.clientList();
        if (info != null && info.length() > 0) {
            JTextArea textArea = new JTextArea(info);
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            tabbedPane.addTab("client list客户端连接信息", new JScrollPane(textArea));
        }
        info = jedis.randomKey();
        if (info != null && info.length() > 0) {
            List<String> timeList = jedis.time();
            JTextArea textArea = new JTextArea(info + ": " + jedis.get(info) + "\nserver time: " +
                    dateFormat.format(new Date(Long.valueOf(timeList.get(0)) * 1000)) + "." + timeList.get(1) + "\ndbsize(keys in the db): "
                    + jedis.dbSize()
            );
            textArea.setColumns(columns);
            textArea.setRows(rows);
            textArea.setEditable(false);
            tabbedPane.addTab("randomkey/time/dbsize", new JScrollPane(textArea));
        }

        JPanel panel = new JPanel();
        tabbedPane.addTab("monitor所有客户端请求", panel);

        panel.setLayout(new FlowLayout(FlowLayout.LEFT));
        panel.setPreferredSize(new Dimension(1000, 450));
        panel.setBackground(new Color(230, 230, 230));
        JLabel label = new JLabel("使用此功能会使服务器性能下降50%！");
        label.setForeground(Color.RED);
        panel.add(label);
        SwingUtil.addNewLineTo(panel);

        JButton buttonStart = new JButton("开始");
        JButton buttonStop = new JButton("停止");
        final AtomicBoolean status = new AtomicBoolean(false);
        panel.add(buttonStart);
        panel.add(buttonStop);
        SwingUtil.addNewLineTo(panel);
        buttonStart.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                status.set(true);
            }
        });
        buttonStop.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                status.set(false);
            }
        });
        final JTextArea monitorInfo = new JTextArea();
        panel.add(new JScrollPane(monitorInfo));
        monitorInfo.setColumns(columns);
        monitorInfo.setRows(rows);
        monitorInfo.setEditable(false);

        jedis.monitor(new JedisMonitor() {
            @Override
            public void onCommand(String command) {
            }
            private long previousUpdateUITime = System.currentTimeMillis();
            private Thread updateUIThread;
            private void onCommand(String command, final Client client, final LinkedList<String> commandList) {
                if (updateUIThread == null) {
                    synchronized (this) {
                        updateUIThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while (true) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                    }
                                    if (status.get() == false)
                                        continue;
                                    if (monitorInfo.isVisible() == false)
                                        continue;

                                    final StringBuilder builder = new StringBuilder();
                                    for (int i = 0; i < commandList.size(); i++) {
                                        builder.append(commandList.get(i)).append("\n");
                                    }
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                if (monitorInfo.isVisible() == false)
                                                    return;
                                                monitorInfo.setText(builder.length() < 1 ? "无信息" : builder.toString());
                                            } catch (Throwable e) {
                                                e.printStackTrace();
                                                status.set(false);
                                            }
                                        }
                                    });
                                }
                            }
                        });
                        updateUIThread.start();
                    }
                }
                if (commandList.size() > 1500) {
                    for (int i = 0; i < 50; i++)
                        commandList.remove(0);
                }
                int dotIndex = command.indexOf(".");
                if (dotIndex > 0) {
                    String time = command.substring(0, command.indexOf("."));
                    command = dateFormat.format(new Date(Long.valueOf(time) * 1000)) + command.substring(command.indexOf("."));
                }
                commandList.add(command);
            }
            public void proceed(final Client client) {
                client.setTimeoutInfinite();
                new Thread(new Runnable() {
                    LinkedList<String> commandList = new LinkedList<String>();

                    @Override
                    public void run() {
                        do {
                            if (status.get() == false) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                }
                                continue;
                            }
                            String command = client.getBulkReply();
                            onCommand(command, client, commandList);
                        } while (client.isConnected() && currentAddress.equals(address));
                    }
                }).start();
            }
        });

        if (selectedIndex < tabbedPane.getTabCount())
            tabbedPane.setSelectedIndex(selectedIndex);
        tabbedPane.setVisible(true);
    }

    protected void initServers() {
        java.util.List<String> clusterNodes = RedisUtil.clusterSlots2Instance(RedisUtil.clusterSlots(null));
        java.util.List<String> shards = RedisUtil.getAllShards();
        for (String node : clusterNodes) {
            if (!shards.contains(node.trim()))
                shards.add(node);
        }
        nodesBox.setVisible(false);
        nodesBox.removeAllItems();
        for (String node : shards) {
            nodesBox.addItem(node);
        }
        nodesBox.setVisible(true);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setInfoRun.run();
            }
        });
    }
}
