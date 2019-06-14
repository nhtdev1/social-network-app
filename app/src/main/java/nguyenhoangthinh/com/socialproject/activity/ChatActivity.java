package nguyenhoangthinh.com.socialproject.activity;

import android.content.Intent;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import nguyenhoangthinh.com.socialproject.R;
import nguyenhoangthinh.com.socialproject.adapters.AdapterChat;
import nguyenhoangthinh.com.socialproject.models.Chat;
import nguyenhoangthinh.com.socialproject.models.User;
import nguyenhoangthinh.com.socialproject.notifications.APIService;
import nguyenhoangthinh.com.socialproject.notifications.Client;
import nguyenhoangthinh.com.socialproject.notifications.Data;
import nguyenhoangthinh.com.socialproject.notifications.Response;
import nguyenhoangthinh.com.socialproject.notifications.Sender;
import nguyenhoangthinh.com.socialproject.notifications.Token;
import nguyenhoangthinh.com.socialproject.widgets.TypingVisualizer;
import retrofit2.Call;
import retrofit2.Callback;

public class ChatActivity extends AppCompatActivity {

    //Firebase
    private FirebaseAuth mAuth;

    private FirebaseDatabase firebaseDatabase;

    private DatabaseReference databaseReference;
    //

    //Để kiểm tra xem người dùng có nhìn thấy tin nhắn hay không
    private ValueEventListener valueEventListener;

    private DatabaseReference referenceForSeen;
    //
    private List<Chat> chatList;

    private AdapterChat adapterChat;

    //UI
    private Toolbar toolbar;

    private RecyclerView recyclerView;

    private ImageView imgProfile;

    private TextView txtName, txtStatus;

    private EditText edtMessage;

    private ImageButton btnSend, btnVideoCall;

    private LinearLayout typingLinearLayout;

    private TypingVisualizer typingVisualizer;
    //

    private String hisUid;

    private String hisImage;

    private String myUid;

    //
    private APIService apiService;

    private boolean notify = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        initializeUI();
    }

    @Override
    protected void onStart() {
        checkUserStatus();
        //set online
        checkOnlineStatus("online");
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //get time stamp
        String timeStamp = String.valueOf(System.currentTimeMillis());

        //set offline with last seen time stamp
        checkOnlineStatus(timeStamp);

        //set typing
        checkTypingStatus("noOne");

        //Người dùng tạm dừng chương trình thì kết thúc việc lắng nghe
        referenceForSeen.removeEventListener(valueEventListener);
    }

    @Override
    protected void onResume() {
        //set online
        checkOnlineStatus("online");
        super.onResume();
    }

    private void initializeUI(){

        android.support.v7.widget.Toolbar toolbar = findViewById(R.id.toolBarChat);
        setSupportActionBar(toolbar);
        toolbar.setTitle("");
        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        typingLinearLayout = findViewById(R.id.typingLayout);
        typingVisualizer   = findViewById(R.id.typingVisualizer);
        recyclerView = findViewById(R.id.recyclerViewChats);
        imgProfile   = findViewById(R.id.circularProfile);
        txtName      = findViewById(R.id.txtName);
        txtStatus    = findViewById(R.id.txtStatus);
        edtMessage   = findViewById(R.id.edtMessage);
        btnSend      = findViewById(R.id.btnSend);
        btnVideoCall = findViewById(R.id.btnVideoCall);
        //Layout linear for recycler view
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        //create API service
        apiService = Client.getRetrofit("https://fcm.googleapis.com/").create(APIService.class);



        mAuth        = FirebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference("User");

        //Khi click vào một user ta có uid. Chúng ta sử dụng UID này để có được hình ảnh và bắt
        //đầu trò chuyện cùng người đó
        Intent intent = getIntent();
        hisUid = intent.getStringExtra("hisUid");

        //Tìm kiếm thông tin bạn chat
        Query query = databaseReference.orderByChild("uid").equalTo(hisUid);

        //Nhận tên và hình ảnh bạn chat
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                //Kiểm tra cho đến khi nhận được thông tin từ firebase
                for(DataSnapshot ds: dataSnapshot.getChildren()){
                    //Nhận tên, uri hình ảnh bạn chat
                    String name   = "" + ds.child("name").getValue();
                    hisImage      = "" + ds.child("image").getValue();

                    //get value of typing
                    String typing = "" + ds.child("typingTo").getValue();
                    if(typing.equals(myUid)){
                        typingVisualizer.setColor(Color.BLUE);
                        typingLinearLayout.setVisibility(View.VISIBLE);
                    }else{
                        typingLinearLayout.setVisibility(View.GONE);
                    }

                    //get value of online status
                    String onlineStatus = ""+ds.child("onlineStatus").getValue();
                    if(onlineStatus.equals("online")) {
                        txtStatus.setText(onlineStatus);
                    }else {
                        //format time
                        Calendar calendar = Calendar.getInstance(Locale.ENGLISH);
                        calendar.setTimeInMillis(Long.parseLong(onlineStatus));
                        String dateTime =
                                DateFormat.format("yyyy/MM/dd hh:mm aa",calendar).toString();
                        txtStatus.setText("Last seen at: "+dateTime);
                    }
                    //Set data
                    txtName.setText(name);
                    try{
                        Picasso.get()
                                .load(hisImage)
                                .placeholder(R.drawable.ic_user_anonymous)
                                .into(imgProfile);
                    }catch (Exception e){
                        Picasso.get()
                                .load(R.drawable.ic_user_anonymous)
                                .into(imgProfile);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        //Bắt sự kiện cho button send để gửi tin nhắn
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                notify = true;
                //Nhận nội dung từ edit text
                String message = edtMessage.getText().toString().trim();

                if(TextUtils.isEmpty(message)){
                    //Handle text is empty
                }else {
                    sendMessage(message);
                }
                //Reset edit text
                edtMessage.setText("");
            }
        });

        //Sự kiện video call
        btnVideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ChatActivity.this,VideoCallActivity.class));
            }
        });

        //check edit text change listener
        edtMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.toString().trim().length() == 0){
                    checkTypingStatus("noOne");
                }else{
                    //uid of receiver
                    checkTypingStatus(hisUid);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        readMessages();

        seenMessages();
    }

    /**
     * Hàm lắng nghe cuộc hội thoại giữa 2 người, cho biết bên nào đã xem
     */
    private void seenMessages() {
        referenceForSeen = FirebaseDatabase.getInstance().getReference("Chats");
        valueEventListener = referenceForSeen.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot ds: dataSnapshot.getChildren()){
                    Chat chat = ds.getValue(Chat.class);
                    //Kiểm tra xem đoạn chat cuối, người nhận là chính bạn và người gửi là bạn của
                    //bạn thì bạn của bạn đã xem tin nhắn
                    if(chat.getReceiver().equals(myUid) && chat.getSender().equals(hisUid)){
                        HashMap<String,Object> hashMap = new HashMap<>();
                        hashMap.put("isSeen",true);
                        ds.getRef().updateChildren(hashMap);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }


    /**
     * Hàm lấy nội dung đoạn chat trên database của firebase sau khi activity chat được gọi
     * và lắng nghe cuộc hội thoại giữa 2 người, khi có sự thay đổi, dữ liệu trên firebase sẽ được
     * cập nhật
     */
    private void readMessages() {
        chatList = new ArrayList<>();
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Chats");
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                chatList.clear();
                for(DataSnapshot ds: dataSnapshot.getChildren()){
                    Chat chat = ds.getValue(Chat.class);
                    //Kiểm tra cho đến khi nhận được nội dung đoạn chat giữa người dùng và bạn chat
                    if((chat.getReceiver().equals(myUid) && chat.getSender().equals(hisUid))
                    ||(chat.getReceiver().equals(hisUid) && chat.getSender().equals(myUid))){
                        chatList.add(chat);
                    }
                }
                adapterChat = new AdapterChat(ChatActivity.this,chatList,hisImage);
                adapterChat.notifyDataSetChanged();
                recyclerView.setAdapter(adapterChat);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    /**
     * @param message , nội dung cần gửi
     *                Hàm gửi nội dung tin nhắn đến bạn chat
     */
    private void sendMessage(final String message) {
        /* "Chats" node will be created that will contains all chats
         * Whenever user send message it will create new child in "Chats" note and that will contain
         * the following key values :
         * sender: UID of sender
         * receiver: UID of receiver
         * message: content of conversation
         */

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();

        //Lấy thời gian hiện tại
        String timestamp = String.valueOf(System.currentTimeMillis());

        HashMap<String,Object> hashMap = new HashMap<>();
        hashMap.put("sender",myUid);
        hashMap.put("receiver",hisUid);
        hashMap.put("message",message);
        hashMap.put("timestamp",timestamp);
        hashMap.put("isSeen",false);
        //Tạo node Chats và set dữ liệu
        reference.child("Chats").push().setValue(hashMap);


        String msg = message;
        DatabaseReference databaseReference =
                FirebaseDatabase.getInstance().getReference("User").child(myUid);
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User  user = dataSnapshot.getValue(User.class);
                if(notify){
                    sendNotifications(hisUid,user.getName(),message);
                }
                notify = false;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void sendNotifications(final String hisUid, final String name, final String message) {
        DatabaseReference allTokens = FirebaseDatabase.getInstance().getReference("Tokens");
        Query query = allTokens.orderByKey().equalTo(hisUid);
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for(DataSnapshot ds:dataSnapshot.getChildren()) {
                    Token token = ds.getValue(Token.class);
                    Data data = new Data(myUid,
                            name+":"+message,
                            "New Message",
                            hisUid,
                            R.drawable.ic_user_anonymous);
                    Sender sender = new Sender(data,token.getToken());
                    apiService.sendNotification(sender)
                            .enqueue(new Callback<Response>() {
                                @Override
                                public void onResponse(Call<Response> call, retrofit2.Response<Response> response) {
                                    Toast.makeText(ChatActivity.this,
                                            response.message(),
                                            Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(Call<Response> call, Throwable t) {

                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    /**
     * Hàm kiểm tra tài khoản người dùng đang được sử dụng hay là đăng xuất
     */
    private void checkUserStatus(){
        //Nhận user hiện tại
        FirebaseUser user = mAuth.getCurrentUser();
        if(user != null){
            //user đã đăng nhập
            myUid = user.getUid();
        }else {
            //User chưa đăng nhập, quay về main activity
            startActivity(new Intent(ChatActivity.this, MainActivity.class));
            finish();
        }
    }

    /**
     * @param status , trạng thái của người dùng đang on hay off
     *               Hàm thay đổi trạng thái của người dùng hiện tại
     */
    private void checkOnlineStatus(String status){
        DatabaseReference reference =
                FirebaseDatabase.getInstance().getReference("User").child(myUid);
        HashMap<String,Object> hashMap = new HashMap<>();
        hashMap.put("onlineStatus",status);

        //Cập nhật giá trị vào trong uid của current user(
        reference.updateChildren(hashMap);
    }

    /**
     * @param typing , trạng thái của người dùng đang gõ tin nhắn
     *               Hàm thay đổi trạng thái của người dùng đang gõ tin nhắn
     */
    private void checkTypingStatus(String typing){
        DatabaseReference reference =
                FirebaseDatabase.getInstance().getReference("User").child(myUid);
        HashMap<String,Object> hashMap = new HashMap<>();
        hashMap.put("typingTo",typing);

        //Cập nhật giá trị vào trong uid của current user(
        reference.updateChildren(hashMap);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if(item.getItemId() == android.R.id.home){
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }
}