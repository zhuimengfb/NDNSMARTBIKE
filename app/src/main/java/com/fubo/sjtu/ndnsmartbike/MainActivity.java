package com.fubo.sjtu.ndnsmartbike;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.fubo.sjtu.ndnsmartbike.Protocol.ForwardInfoGenerator;
import com.fubo.sjtu.ndnsmartbike.Protocol.InterestPacketGenerator;
import com.fubo.sjtu.ndnsmartbike.adapter.MyRecyclerViewAdapter;
import com.fubo.sjtu.ndnsmartbike.database.ActivityInfoDataHelper;
import com.fubo.sjtu.ndnsmartbike.database.ForwardInfoDataHelper;
import com.fubo.sjtu.ndnsmartbike.model.ActivityInfo;
import com.fubo.sjtu.ndnsmartbike.model.ForwardInfo;
import com.fubo.sjtu.ndnsmartbike.model.InterestPacket;
import com.fubo.sjtu.ndnsmartbike.service.BluetoothService;
import com.fubo.sjtu.ndnsmartbike.utils.GlobalMember;
import com.fubo.sjtu.ndnsmartbike.view.BluetoothActivity;
import com.fubo.sjtu.ndnsmartbike.view.ForwardInfoActivity;
import com.fubo.sjtu.ndnsmartbike.view.PulishActivity;
import com.fubo.sjtu.ndnsmartbike.view.UserLoginActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements NavigationView
        .OnNavigationItemSelectedListener {

    private NavigationView navigationView;
    private RecyclerView mRecyclerView;
    private MyRecyclerViewAdapter myRecyclerViewAdapter;
    private List<ActivityInfo> activityInfos = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private MyReceiver myReceiver;
    private MyHandler myHandler;

    private static final int MESSAGE_UPDATE_RECYCLERVIEW=1;
    private static final int MESSAGE_START_REFRESHING=2;
    private static final int MESSAGE_CANCLE_REFRESHING=3;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GlobalMember.ACTION_HAS_NEW_ACTIVITY);
        intentFilter.addAction(GlobalMember.ACTION_HAS_NOT_NEW_ACTIVITY);
        myReceiver = new MyReceiver();
        registerReceiver(myReceiver, intentFilter);

        myHandler = new MyHandler();

        initView();
        initData();
        initEvent();
    }

    class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_UPDATE_RECYCLERVIEW)
                myRecyclerViewAdapter.notifyDataSetChanged();
            else if (msg.what==MESSAGE_START_REFRESHING) {
                swipeRefreshLayout.setRefreshing(true);
                Timer timer=new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (swipeRefreshLayout.isRefreshing()){
                            Message message=new Message();
                            message.what=MESSAGE_CANCLE_REFRESHING;
                            myHandler.sendMessage(message);
                        }
                    }
                },10000);
            }
            else if (msg.what==MESSAGE_CANCLE_REFRESHING) {
                Intent intent = new Intent();
                intent.setAction(GlobalMember.ACTION_HAS_NOT_NEW_ACTIVITY);
                sendBroadcast(intent);
//                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getAction()) {
                    case GlobalMember.ACTION_HAS_NEW_ACTIVITY:
                        Toast.makeText(getApplicationContext(), getResources().getString(R.string
                                .has_new_activity), Toast.LENGTH_SHORT).show();
                        //重新查询数据库更新
                        List<ActivityInfo> activityInfos1 = ActivityInfoDataHelper.getInstance
                                (getApplicationContext()).selectAllValidActivity();
                        activityInfos.clear();
                        activityInfos.addAll(activityInfos1);
                        myRecyclerViewAdapter.notifyDataSetChanged();
                        swipeRefreshLayout.setRefreshing(false);
                        break;
                    case GlobalMember.ACTION_HAS_NOT_NEW_ACTIVITY:
                        Toast.makeText(getApplicationContext(), getResources().getString(R.string
                                .has_not_new_activity), Toast.LENGTH_SHORT).show();
                        swipeRefreshLayout.setRefreshing(false);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void initView() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, PulishActivity.class);
                startActivityForResult(intent, 1);
            }
        });
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string
                .navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
        initNavigationView();
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));
    }

    //从数据库中查找数据
    private void initData() {
        myRecyclerViewAdapter = new MyRecyclerViewAdapter(getApplicationContext(), activityInfos);
        mRecyclerView.setAdapter(myRecyclerViewAdapter);
        myHandler.post(new Runnable() {
            @Override
            public void run() {
                ActivityInfoDataHelper activityInfoDataHelper = ActivityInfoDataHelper
                        .getInstance(getApplicationContext());
                List<ActivityInfo> activityInfos1 = activityInfoDataHelper.selectAllValidActivity();
                Message message = new Message();//更新UI的消息
                if (activityInfos1.size() == 0) {
                    //发送请求所有数据的兴趣包，先打包一个兴趣包，发送兴趣包，生成一个转发包，存储转发包
                    /*InterestPacket interestPacket = InterestPacketGenerator
                            .generateRequestAllInterestPacket();
                    BluetoothService.sendData(getApplicationContext(), InterestPacketGenerator
                            .generateSendInterestPacket(interestPacket).getBytes());
                    ForwardInfo forwardInfo = ForwardInfoGenerator
                            .generateForwardInfoFromInterestPacket(interestPacket);
                    ForwardInfoDataHelper forwardInfoDataHelper = ForwardInfoDataHelper
                            .getInstance(getApplicationContext());
                    forwardInfoDataHelper.insertForwardInfo(forwardInfo);
                    message.what = MESSAGE_START_REFRESHING;*/
                } else {
                    activityInfos.addAll(activityInfos1);
                    message.what = MESSAGE_UPDATE_RECYCLERVIEW;
                }
                myHandler.sendMessage(message);
            }
        });
    }

    private void initEvent() {
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Message message=new Message();
                message.what=MESSAGE_START_REFRESHING;
                myHandler.sendMessage(message);
                //发送请求新的活动兴趣包
                InterestPacket interestPacket = InterestPacketGenerator
                        .generateRequestNewInterestPacket();
                ActivityInfoDataHelper activityInfoDataHelper = ActivityInfoDataHelper
                        .getInstance(getApplicationContext());
                //添加筛选条件
                List<ActivityInfo> activityInfos = activityInfoDataHelper.selectAllValidActivity();
                String meta = "";
                for (int i = 0; i < activityInfos.size(); i++) {
                    meta += activityInfos.get(i).getId() + "/";
                }
                interestPacket.setMeta(meta);
                String data = InterestPacketGenerator.generateSendInterestPacket(interestPacket);
                BluetoothService.sendData(getApplicationContext(), data.getBytes());

                ForwardInfo forwardInfo = ForwardInfoGenerator
                        .generateForwardInfoFromInterestPacket(interestPacket);
                ForwardInfoDataHelper forwardInfoDataHelper = ForwardInfoDataHelper.getInstance
                        (getApplicationContext());
                forwardInfoDataHelper.insertForwardInfo(forwardInfo);
            }
        });
    }

    private void initNavigationView() {
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        setNavHeaderView(navigationView);
    }

    private void setNavHeaderView(NavigationView navigationView) {
        View view = navigationView.getHeaderView(0);
        ((TextView) (view.findViewById(R.id.userName))).setText(MyApplication.getUser()
                .getUserName());
        ((TextView) (view.findViewById(R.id.userDes))).setText(MyApplication.getUser().getUserDes
                ());
        //设置默认头像
        if ("".equals(MyApplication.getUser().getUserImage())) {

        }
        //获取用户头像
        else {

        }
        (view.findViewById(R.id.imageView)).setOnClickListener(new UserClickListener());
    }

    //用户注册或修改
    class UserClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, UserLoginActivity.class);
            startActivityForResult(intent, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == 0)
            setNavHeaderView(navigationView);
        else if (resultCode==1){
            ActivityInfoDataHelper activityInfoDataHelper = ActivityInfoDataHelper
                    .getInstance(getApplicationContext());
            List<ActivityInfo> activityInfos1 = activityInfoDataHelper.selectAllValidActivity();
            activityInfos.clear();
            activityInfos.addAll(activityInfos1);
            myRecyclerViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent();
            intent.setClass(this, BluetoothActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camara) {
            // Handle the camera action
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, ForwardInfoActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myReceiver);
    }
}
