package nguyenhoangthinh.com.socialproject.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;
import nguyenhoangthinh.com.socialproject.R;

public class VideoCallActivity extends AppCompatActivity {

    // Permissions
    private static final int PERMISSION_REQ_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS =
            {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};

    private static final String LOG_TAG = VideoCallActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID)) {
            initAgoraEngine();
        }

        onjoinChannelClicked();
    }

    /**
     * @param permission
     * @param requestCode
     * @return true nếu ứng dụng được cấp quyền record audio và camera, ngược lại false
     */
    public boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    REQUESTED_PERMISSIONS,
                    requestCode);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        Log.i(LOG_TAG, "onRequestPermissionsResult " + grantResults[0] + " " + requestCode);

        switch (requestCode) {
            case PERMISSION_REQ_ID: {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                        grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(LOG_TAG, "Need permissions " +
                            Manifest.permission.RECORD_AUDIO +
                            "/" + Manifest.permission.CAMERA);
                    break;
                }
                // if permission granted, initialize the engine
                initAgoraEngine();
                break;
            }
        }
    }

    // Khởi tạo SDK Agora.io
    /* Trước khi chúng ta có thể đi sâu vào việc khởi tạo, chúng ta cần đảm bảo rằng Hoạt động của
     * chúng ta có quyền truy cập vào một phiên bản của Agora.io RtcEngine.
     * */

    // Khai báo thuộc tính lớp để lưu trữ thể hiện của RtcEngine.
    private RtcEngine mRtcEngine;

    // Tạo một phiên bản mới của RtcEngine bằng cách sử dụng baseContext, id ứng dụng Agora(đã khai
    // báo và một phiên bản của RtcEngineEventHandler
    private void initAgoraEngine() {
        try {
            //Lấy được phiên bản mới nhất của RtcEngine
            mRtcEngine =
                    RtcEngine.create(getBaseContext(), getString(R.string.agora_app_id), mRtcEventHandler);
        } catch (Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));

            throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" +
                    Log.getStackTraceString(e));
        }
        setupSession();
    }

    // Thiết lập phiên của người dùng. Ở đây chúng ta có thể set Channel Profile to Communication,
    // vì đây là trò chuyện video chứ không phải broadcast
    private void setupSession() {
        mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);

        mRtcEngine.enableVideo();

        mRtcEngine
                .setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                        VideoEncoderConfiguration.VD_1920x1080,
                        VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                        VideoEncoderConfiguration.STANDARD_BITRATE,
                        VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT));
    }

    private void setupLocalVideoFeed() {
        //tham chiếu cho view sẽ đóng vai trò là chế độ xem chính cho luồng video .
        FrameLayout videoContainer = findViewById(R.id.floating_video_container);

        // Bước thứ hai là sử dụng RtcEngine để tạo ra một SurfaceView rằng sẽ làm cho dòng từ camera
        // phía trước, chúng tôi cũng đặt mới videoSurface để hiển thị trên đầu view parent
        SurfaceView videoSurface = RtcEngine.CreateRendererView(getBaseContext());

        //Bước tiếp theo là thêm phần videoSurface dưới dạng phần tử giao diện người dùng
        videoSurface.setZOrderMediaOverlay(true);
        videoContainer.addView(videoSurface);

        //Đểể trống tham số uid để SDK có thể xử lý việc tạo id động cho mỗi người dùng.
        mRtcEngine.setupLocalVideo(new VideoCanvas(videoSurface, VideoCanvas.RENDER_MODE_FIT, 0));


    }

    // Thiết lập nguồn cấp dữ liệu video cục bộ, chúng ta cần sử dụng một chức năng tương tự
    // để kết nối luồng video từ xa.
    private void setupRemoteVideoStream(int uid) {
        FrameLayout videoContainer = findViewById(R.id.bg_video_container);
        SurfaceView videoSurface = RtcEngine.CreateRendererView(getBaseContext());
        videoContainer.addView(videoSurface);
        mRtcEngine.setupRemoteVideo(new VideoCanvas(videoSurface, VideoCanvas.RENDER_MODE_FIT, uid));

        mRtcEngine.setRemoteSubscribeFallbackOption(io.agora.rtc.Constants.STREAM_FALLBACK_OPTION_AUDIO_ONLY);
        // Sự khác biệt chính với video từ xa từ cục bộ, là tham số id người dùng được truyền cho
        // engine như một phần của VideoCanvas đối tượng được truyền vào engine. Dòng cuối cùng đặt
        // tùy chọn quay lại trong trường hợp video xuống cấp, engine sẽ chỉ trở lại âm thanh.
    }

    // Thiết lập Trình xử lý sự kiện SDK

    // Trước đó đã tham chiếu đến RtcEngineEventHandler , và khái báo engine này là
    // của MainActivity. Công cụ sẽ gọi các phương thức này từ RtcEngineEventHandler .

    // Handle SDK Events
    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onFirstRemoteVideoDecoded(final int uid, int width, int height, int elapsed) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setupRemoteVideoStream(uid);
                }
            });
        }

        // remote user has left channel
        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onRemoteUserLeft();
                }
            });
        }

        // remote stream has been toggled
        @Override
        public void onUserMuteVideo(final int uid, final boolean toggle) { // Tutorial Step 10
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onRemoteUserVideoToggle(uid, toggle);
                }
            });
        }
    };

    // Each event triggers some fairly straight forward functions,
    // including one we wrote in the previous step
    private void onRemoteUserVideoToggle(int uid, boolean toggle) {
        FrameLayout videoContainer = findViewById(R.id.bg_video_container);

        SurfaceView videoSurface = (SurfaceView) videoContainer.getChildAt(0);
        videoSurface.setVisibility(toggle ? View.GONE : View.VISIBLE);

        // add an icon to let the other user know remote video has been disabled
        if(toggle){
            ImageView noCamera = new ImageView(this);
            noCamera.setImageResource(R.drawable.video_call_enable);
            videoContainer.addView(noCamera);
        } else {
            ImageView noCamera = (ImageView) videoContainer.getChildAt(1);
            if(noCamera != null) {
                videoContainer.removeView(noCamera);
            }
        }
    }

    private void onRemoteUserLeft() {
        removeVideo(R.id.bg_video_container);
    }

    private void removeVideo(int containerID) {
        FrameLayout videoContainer = findViewById(containerID);
        videoContainer.removeAllViews();
    }

    // Tham gia và rời khỏi kênh
    //Lưu ý: nếu bạn không chỉ định uid khi tham gia kênh, công cụ sẽ chỉ định một uid default
    public void onjoinChannelClicked() {
        mRtcEngine.joinChannel(null,
                "test-channel",
                "Extra Optional Data",
                0);
        setupLocalVideoFeed();
        // Như bạn có thể thấy từ dòng đầu tiên, Agora SDK làm cho nó đơn giản, engine gọi joinChannel,
        // chuyển qua tên kênh là cuộc gọi để thiết lập luồng video cục bộ của chúng tôi.
    }


    //Rời khỏi kênh
    public void onLeaveChannelClicked(View view) {
        leaveChannel();
        FrameLayout videoContainer1 = findViewById(R.id.floating_video_container);
        removeVideo(videoContainer1);
        FrameLayout videoContainer2 = findViewById(R.id.bg_video_container);
        removeVideo(videoContainer2);
        finish();
    }

    private void leaveChannel() {
        mRtcEngine.leaveChannel();
    }

    private void removeVideo(FrameLayout videoContainer) {
        videoContainer.removeAllViews();
    }

    // Đầu tiên, chúng tôi nhận được tham chiếu đến nút của chúng tôi, và sau đó kiểm tra xem nó
    // đã được bật/tắt bằng cách sử dụng chưa isSelected(). Khi chúng tôi đã cập nhật trạng thái
    // thành phần UI, chúng tôi chuyển trạng thái cập nhật của nút cho công cụ.
    public void onAudioMuteClicked(View view) {
        ImageView btn = (ImageView) view;
        if (btn.isSelected()) {
            btn.setSelected(false);
            btn.setImageResource(R.drawable.microphone_disabled);
        } else {
            btn.setSelected(true);
            btn.setImageResource(R.drawable.microphone_enable);
        }

        mRtcEngine.muteLocalAudioStream(btn.isSelected());
    }

    //Cũng như chuyển đổi âm thanh, chúng tôi kiểm tra / cập nhật trạng thái của nút bằng cách sử dụng
    // isSelected()và sau đó chuyển nó sang động cơ. Để thể hiện hình ảnh tốt hơn của video bị
    // tắt tiếng, chúng tôi ẩn / hiển thị videoSurface.
    public void onVideoMuteClicked(View view) {
        ImageView btn = (ImageView) view;
        if (btn.isSelected()) {
            btn.setSelected(false);
            btn.setImageResource(R.drawable.video_call_enable);
        } else {
            btn.setSelected(true);
            btn.setImageResource(R.drawable.video_call_disabled);
        }

        mRtcEngine.muteLocalVideoStream(btn.isSelected());

        FrameLayout container = findViewById(R.id.floating_video_container);
        container.setVisibility(btn.isSelected() ? View.GONE : View.VISIBLE);
        SurfaceView videoSurface = (SurfaceView) container.getChildAt(0);
        videoSurface.setZOrderMediaOverlay(!btn.isSelected());
        videoSurface.setVisibility(btn.isSelected() ? View.GONE : View.VISIBLE);
    }

}