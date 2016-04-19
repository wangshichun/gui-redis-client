package com.dangdang.tools.redis;

import com.dangdang.tools.redis.event.ExtractRedisAddress;
import com.dangdang.tools.redis.util.RedisUtil;
import com.dangdang.tools.redis.util.SwingUtil;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedis;
import redis.clients.util.Hashing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Created by wangshichun on 2015/12/7.
 */
public class RedisJFrame extends JFrame {
//    private static final String DEFAULT_ZOO_ADDR = "10.255.209.177:2181/redisCluster/order/zkRedisClusterStatus";
    private static final String DEFAULT_ZOO_ADDR = "10.255.209.47:6379";
    private String[] addresses = new String[] {
            "10.255.242.19:2183/redisCluster/order/zkRedisClusterStatus",
            "10.255.242.21:2183/redisCluster/transaction/zkRedisClusterStatus",
            "10.255.242.22:2183/redisCluster/invoice/zkRedisClusterStatus",
            "10.255.242.22:2183/redisCluster/productStock/zkRedisClusterStatus",
            "10.255.209.47:6379"
    };
    private JLabel tipsLabel;
    private JTextField redisPasswordText;
    private RedisPanel redisPanel = new RedisPanel();

    public RedisJFrame() {
        this.setTitle("redis 工具");
        this.setSize(SwingUtil.SCREEN_SIZE.width, SwingUtil.SCREEN_SIZE.height - 150);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.getContentPane().setLayout(new FlowLayout(FlowLayout.LEFT));

        // 添加控件
        JLabel label = new JLabel("当当zookeeper地址（"
                + "格式为“10.255.242.19:2183/redisCluster/order/zkRedisClusterStatus”"
                + "），或redis地址（格式为“10.255.209.177:6379”）：");
        this.add(label);
        SwingUtil.addNewLineTo(this);
        SwingUtil.addSpaceTo(this, 4);

        // zookeeper或者redis的输入框
        final JTextField textField = new JTextField(SwingUtil.DEFAULT_TEXT_FIELD_COLUMNS);
        textField.setText(DEFAULT_ZOO_ADDR);
        textField.setComponentPopupMenu(SwingUtil.makePopupMenu());
        this.add(textField);
        this.add(new JLabel("redis的密码(如有):"));
        redisPasswordText = new JTextField(SwingUtil.DEFAULT_TEXT_FIELD_COLUMNS / 2);
        redisPasswordText.setComponentPopupMenu(SwingUtil.makePopupMenu());
        this.add(redisPasswordText);

        // 按钮
        final JButton connectButton = new JButton("连接");
        this.add(connectButton);
        JButton changeAddressButton = new JButton("换");
        this.add(changeAddressButton);
        changeAddressButton.setForeground(Color.LIGHT_GRAY);
        changeAddressButton.setMargin(new Insets(0, 0, 0, 0));
        changeAddressButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (addresses.length <= 1)
                    return;

                boolean found = false;
                for (int i = 0; i < addresses.length; i++) {
                    String addr = addresses[i];
                    if (addr.equals(textField.getText().trim())) {
                        found = true;
                        if (i == addresses.length - 1) {
                            textField.setText(addresses[0]);
                        } else {
                            textField.setText(addresses[i + 1]);
                        }
                        textField.setCaretPosition(0);
                        return;
                    }
                }
                if (!found) {
                    textField.setText(addresses[0]);
                    textField.setCaretPosition(0);
                }
            }
        });

        // 提示信息显示
        tipsLabel = new JLabel();
        this.add(tipsLabel);
        SwingUtil.addNewLineTo(this);

        // 添加redis的panel
        this.getContentPane().add(redisPanel);

        // 事件关联
        connectButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                initAddr(textField.getText());
            }
        });
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    initAddr(textField.getText());
                } else {
                    super.keyPressed(e);
                }
            }
        });

        this.doLayout();
        this.setVisible(true);
    }

    private void initAddr(String address) {
        try {
            List<JedisShardInfo> shardInfos = new ExtractRedisAddress().extract(address);
            RedisUtil.init(shardInfos, redisPasswordText.getText().trim());
            redisPanel.setShardedJedisInfo();
            setTips(shardInfos);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            setTipsLabelError(throwable.getMessage());
        }
    }

    private void setTips(List<JedisShardInfo> shardInfos) {
        if (null == shardInfos || shardInfos.isEmpty()) {
            tipsLabel.setForeground(Color.RED);
            tipsLabel.setText("连接失败");
        } else {
            tipsLabel.setForeground(Color.GREEN);
            tipsLabel.setText("连接成功");
        }
    }
    private void setTipsLabelError(String msg) {
        tipsLabel.setForeground(Color.RED);
        tipsLabel.setText("错误：" + msg);
    }

    public static void main(String[] args) {
        new RedisJFrame();
    }
}
