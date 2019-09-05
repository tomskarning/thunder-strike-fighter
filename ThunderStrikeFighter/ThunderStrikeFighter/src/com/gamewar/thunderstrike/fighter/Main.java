package com.gamewar.thunderstrike.fighter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetFileDescriptor;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.example.games.basegameutils.BaseGameActivity;
import com.gamewar.thunderstrike.fighter.R;

public class Main extends BaseGameActivity {
	Handler h = new Handler();
	SharedPreferences sp;
	Editor ed;
	boolean isForeground = true;
	MediaPlayer mp;
	SoundPool sndpool;
	int snd_result;
	int snd_fire;
	int snd_explode;
	int score;
	int screen_width;
	int screen_height;
	int current_section = R.id.main;
	boolean show_leaderboard;
	AnimationDrawable anim_explode;
	List<View> enemies = new ArrayList<View>();
	List<Float> enemies_x = new ArrayList<Float>();
	List<Float> enemies_y = new ArrayList<Float>();
	View hero;
	View rocket;
	View explode;
	boolean game_paused;
	float speed_x;
	float speed_y;
	float rotation;
	boolean fire;
	final float rocket_speed = 15f; // rocket speed
	final float rotation_speed = 10f; // hero rotation speed
	final float emeny_speed = 0.5f; // enemy speed

	// AdMob
	AdView adMob_smart;
	InterstitialAd adMob_interstitial;
	final boolean show_admob_smart = true; // show AdMob Smart banner
	final boolean show_admob_interstitial = true; // show AdMob Interstitial

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// fullscreen
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// preferences
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		ed = sp.edit();

		// AdMob smart
		add_admob_smart();

		// bg sound
		mp = new MediaPlayer();
		try {
			AssetFileDescriptor descriptor = getAssets().openFd("snd_bg.mp3");
			mp.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
			descriptor.close();
			mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mp.setLooping(true);
			mp.setVolume(0, 0);
			mp.prepare();
			mp.start();
		} catch (Exception e) {
		}

		// if mute
		if (sp.getBoolean("mute", false)) {
			((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_sound));
		} else {
			mp.setVolume(0.5f, 0.5f);
		}

		// SoundPool
		sndpool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
		try {
			snd_result = sndpool.load(getAssets().openFd("snd_result.mp3"), 1);
			snd_explode = sndpool.load(getAssets().openFd("snd_explode.mp3"), 1);
			snd_fire = sndpool.load(getAssets().openFd("snd_fire.mp3"), 1);
		} catch (IOException e) {
		}

		// hide navigation bar listener
		findViewById(R.id.all).setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
			@Override
			public void onSystemUiVisibilityChange(int visibility) {
				hide_navigation_bar();
			}
		});

		// elements
		hero = findViewById(R.id.hero);
		rocket = findViewById(R.id.rocket);
		explode = findViewById(R.id.explode);

		// animation explode
		anim_explode = (AnimationDrawable) ((ImageView) explode).getDrawable();
		anim_explode.start();

		// add enemies
		for (int i = 0; i < 20; i++) {
			ImageView enemy = new ImageView(this);
			enemy.setBackgroundResource(R.drawable.enemy);
			enemy.setLayoutParams(new LayoutParams((int) DpToPx(26), (int) DpToPx(22)));
			((ViewGroup) findViewById(R.id.game)).addView(enemy);
			enemies.add(enemy);
			enemies_x.add(0f);
			enemies_y.add(0f);

			if (i >= 10)
				enemy.setEnabled(false);
		}

		// custom font
		Typeface font = Typeface.createFromAsset(getAssets(), "CooperBlack.otf");
		((TextView) findViewById(R.id.txt_result)).setTypeface(font);
		((TextView) findViewById(R.id.txt_high_result)).setTypeface(font);
		((TextView) findViewById(R.id.txt_score)).setTypeface(font);
		((TextView) findViewById(R.id.mess)).setTypeface(font);

		// index
		explode.bringToFront();
		findViewById(R.id.txt_score).bringToFront();
		findViewById(R.id.btn_play).bringToFront();
		findViewById(R.id.mess).bringToFront();

		// touch listener
		findViewById(R.id.game).setOnTouchListener(new OnTouchListener() {
			@SuppressLint("ClickableViewAccessibility")
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// fire
				if (current_section == R.id.game && event.getAction() == MotionEvent.ACTION_DOWN) {
					// distance
					float distance = (float) Math.sqrt(Math.pow(event.getX() - hero.getX() - hero.getWidth() * 0.5f, 2)
							+ Math.pow(event.getY() - hero.getY() - hero.getHeight() * 0.5f, 2));

					// angle
					float cos = (event.getX() - hero.getX() - hero.getWidth() * 0.5f) / distance;
					float sin = (event.getY() - hero.getY() - hero.getHeight() * 0.5f) / distance;
					if (cos < -1)
						cos = -1;
					if (cos > 1)
						cos = 1;

					// hero target rotation
					if (event.getY() > hero.getY() + hero.getHeight() * 0.5f)
						rotation = (float) (Math.acos(cos) * 180f / Math.PI);
					else
						rotation = (float) (-Math.acos(cos) * 180f / Math.PI);
					if (rotation < 0)
						rotation = 360 + rotation;
					if (rotation == 360)
						rotation = 0;

					// rocket speed
					if (rocket.isEnabled()) {
						speed_x = cos * DpToPx(rocket_speed);
						speed_y = sin * DpToPx(rocket_speed);
					}

					fire = true;
				}
				return false;
			}
		});

		SCALE();
	}

	// SCALE
	void SCALE() {
		// btn_play
		FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) findViewById(R.id.btn_play).getLayoutParams();
		l.width = (int) DpToPx(50);
		l.height = (int) DpToPx(50);
		l.setMargins(0, (int) DpToPx(7), (int) DpToPx(7), 0);
		findViewById(R.id.btn_play).setLayoutParams(l);

		// txt_time
		((TextView) findViewById(R.id.txt_score)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(22));
		l = (FrameLayout.LayoutParams) findViewById(R.id.txt_score).getLayoutParams();
		l.setMargins((int) DpToPx(5), 0, 0, 0);
		findViewById(R.id.txt_score).setLayoutParams(l);

		// hero
		hero.getLayoutParams().width = (int) DpToPx(26);
		hero.getLayoutParams().height = (int) DpToPx(30);

		// explode
		explode.getLayoutParams().width = (int) DpToPx(80);
		explode.getLayoutParams().height = (int) DpToPx(80);

		// rocket
		rocket.getLayoutParams().width = (int) DpToPx(10);
		rocket.getLayoutParams().height = (int) DpToPx(5);

		// text mess
		((TextView) findViewById(R.id.mess)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(30));

		// buttons text
		((TextView) findViewById(R.id.btn_sign)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_leaderboard)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_sound)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(16));
		((TextView) findViewById(R.id.btn_start)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(26));
		((TextView) findViewById(R.id.btn_exit)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(16));
		((TextView) findViewById(R.id.btn_home)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));
		((TextView) findViewById(R.id.btn_start2)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(28));

		// text result
		((TextView) findViewById(R.id.txt_result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(40));
		((TextView) findViewById(R.id.txt_high_result)).setTextSize(TypedValue.COMPLEX_UNIT_PX, DpToPx(20));
	}

	// START
	void START() {
		show_section(R.id.game);
		score = 0;
		speed_x = 0;
		speed_y = 0;
		findViewById(R.id.mess).setVisibility(View.GONE);
		game_paused = false;
		fire = false;
		((TextView) findViewById(R.id.txt_score)).setText(getString(R.string.score) + " " + score);
		findViewById(R.id.btn_play).setVisibility(View.VISIBLE);
		((ToggleButton) findViewById(R.id.btn_play)).setChecked(true);
		explode.setX(-DpToPx(1000));
		explode.setY(-DpToPx(1000));
		rocket.setEnabled(true);
		hero.setVisibility(View.VISIBLE);
		rocket.setVisibility(View.VISIBLE);

		// screen size
		screen_width = Math.min(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());
		screen_height = Math.max(findViewById(R.id.all).getWidth(), findViewById(R.id.all).getHeight());

		// hero
		hero.setX((screen_width - hero.getWidth()) * 0.5f);
		hero.setY((screen_height - hero.getHeight()) * 0.5f);
		hero.setRotation(270);
		rotation = hero.getRotation();

		// rocket
		rocket.setX((screen_width - rocket.getWidth()) * 0.5f);
		rocket.setY((screen_height - rocket.getHeight()) * 0.5f);
		rocket.setRotation(hero.getRotation());

		// random enemies
		for (int i = 0; i < enemies.size(); i++) {
			random_enemy(i);
		}

		MOVE.run();
	}

	// random_enemy
	void random_enemy(int i) {
		int random_angle = (int) (Math.random() * 360);
		float distance = (float) (Math.sqrt(Math.pow(screen_width, 2) + Math.pow(screen_height, 2)) * 0.5f + hero.getWidth() * 2f);
		distance += (Math.random() * (distance * 0.3f));

		// position
		enemies.get(i).setX((float) (Math.cos(random_angle * Math.PI / 180f) * distance + screen_width * 0.5f));
		enemies.get(i).setY((float) (Math.sin(random_angle * Math.PI / 180f) * distance + screen_height * 0.5f));

		// rotation
		float cos = (screen_width * 0.5f - enemies.get(i).getX() - enemies.get(i).getWidth() * 0.5f) / distance;
		float sin = (screen_height * 0.5f - enemies.get(i).getY() - enemies.get(i).getHeight() * 0.5f) / distance;
		if (cos < -1)
			cos = -1;
		if (cos > 1)
			cos = 1;
		enemies.get(i).setRotation(random_angle);

		// speed
		enemies_x.set(i, cos * DpToPx(emeny_speed));
		enemies_y.set(i, sin * DpToPx(emeny_speed));
	}

	// MOVE
	Runnable MOVE = new Runnable() {
		@Override
		public void run() {
			if (!game_paused) {
				// hero rotation
				if (rotation > hero.getRotation()) {
					if (Math.abs(rotation - hero.getRotation()) < 180)
						hero.setRotation(Math.min(hero.getRotation() + rotation_speed, rotation));
					else
						hero.setRotation(Math.max(hero.getRotation() - rotation_speed, rotation - 360));
				} else if (rotation < hero.getRotation()) {
					if (Math.abs(rotation - hero.getRotation()) < 180)
						hero.setRotation(Math.max(hero.getRotation() - rotation_speed, rotation));
					else
						hero.setRotation(Math.min(hero.getRotation() + rotation_speed, 360 + rotation));
				} else if (fire && rocket.isEnabled()) {
					// fire
					rocket.setEnabled(false);

					// sound
					if (!sp.getBoolean("mute", false) && isForeground)
						sndpool.play(snd_fire, 0.1f, 0.1f, 0, 0, 1);
				}

				if (hero.getRotation() > 360)
					hero.setRotation(hero.getRotation() - 360);
				if (hero.getRotation() < 0)
					hero.setRotation(360 + hero.getRotation());

				// rocket rotation
				if (rocket.isEnabled())
					rocket.setRotation(hero.getRotation());
				else {
					// move rocket
					rocket.setX(rocket.getX() + speed_x);
					rocket.setY(rocket.getY() + speed_y);
				}

				// rocket off screen
				if (rocket.getX() < -DpToPx(10) || rocket.getY() < -DpToPx(10) || rocket.getX() > screen_width + DpToPx(10)
						|| rocket.getY() > screen_height + DpToPx(10)) {
					rocket.setX((screen_width - rocket.getWidth()) * 0.5f);
					rocket.setY((screen_height - rocket.getHeight()) * 0.5f);
					rocket.setRotation(hero.getRotation());
					rocket.setEnabled(true);
					fire = false;
				}

				// enemies
				for (int i = 0; i < enemies.size(); i++) {
					// move
					if (enemies.get(i).isEnabled()) {
						enemies.get(i).setX(enemies.get(i).getX() + enemies_x.get(i));
						enemies.get(i).setY(enemies.get(i).getY() + enemies_y.get(i));

						if (hero.getVisibility() == View.VISIBLE) {
							// enemy hit rectangle
							RectF enemy_rect = new RectF(enemies.get(i).getX() + enemies.get(i).getWidth() * 0.5f - DpToPx(10),
									enemies.get(i).getY() + enemies.get(i).getHeight() * 0.5f - DpToPx(10), enemies.get(i).getX()
											+ enemies.get(i).getWidth() * 0.5f + DpToPx(10), enemies.get(i).getY()
											+ enemies.get(i).getHeight() * 0.5f + DpToPx(10));

							// hit hero with enemy
							if (enemy_rect.intersect(new RectF(screen_width * 0.5f - DpToPx(6), screen_height * 0.5f - DpToPx(6),
									screen_width * 0.5f + DpToPx(6), screen_height * 0.5f + DpToPx(6)))) {
								// hide hero
								hero.setVisibility(View.GONE);
								rocket.setVisibility(View.GONE);

								// explode
								explode.setX(hero.getX() - (explode.getWidth() - hero.getWidth()) * 0.5f);
								explode.setY(hero.getY() - (explode.getHeight() - hero.getHeight()) * 0.5f);
								anim_explode.stop();
								anim_explode.start();

								// sound
								if (!sp.getBoolean("mute", false) && isForeground)
									sndpool.play(snd_explode, 0.8f, 0.8f, 0, 0, 1);

								// message
								findViewById(R.id.mess).setVisibility(View.VISIBLE);

								// game over
								h.postDelayed(STOP, 3000);
							}

							// hit rocket with enemy
							if (enemy_rect.intersect(new RectF(rocket.getX() + rocket.getWidth() * 0.5f, rocket.getY()
									+ rocket.getHeight() * 0.5f, rocket.getX() + rocket.getWidth() * 0.5f, rocket.getY()
									+ rocket.getHeight() * 0.5f))) {
								// explode
								explode.setX(enemies.get(i).getX() - (explode.getWidth() - enemies.get(i).getWidth()) * 0.5f);
								explode.setY(enemies.get(i).getY() - (explode.getHeight() - enemies.get(i).getHeight()) * 0.5f);
								anim_explode.stop();
								anim_explode.start();

								random_enemy(i);

								// score
								score += 5;
								((TextView) findViewById(R.id.txt_score)).setText(getString(R.string.score) + " " + score);

								// enable enemies
								switch (score) {
								case 100:
									enemies.get(10).setEnabled(true);
									break;
								case 200:
									enemies.get(11).setEnabled(true);
									break;
								case 300:
									enemies.get(12).setEnabled(true);
									break;
								case 400:
									enemies.get(13).setEnabled(true);
									break;
								case 500:
									enemies.get(14).setEnabled(true);
									break;
								case 600:
									enemies.get(15).setEnabled(true);
									break;
								case 700:
									enemies.get(16).setEnabled(true);
									break;
								case 800:
									enemies.get(17).setEnabled(true);
									break;
								case 900:
									enemies.get(18).setEnabled(true);
									break;
								case 1000:
									enemies.get(19).setEnabled(true);
									break;
								}

								// sound
								if (!sp.getBoolean("mute", false) && isForeground)
									sndpool.play(snd_explode, 0.6f, 0.6f, 0, 0, 1);

								// rocket start position
								rocket.setX((screen_width - rocket.getWidth()) * 0.5f);
								rocket.setY((screen_height - rocket.getHeight()) * 0.5f);
								rocket.setRotation(hero.getRotation());
								rocket.setEnabled(true);
								fire = false;
							}
						}
					}
				}
			}

			h.postDelayed(MOVE, 10);
		}
	};

	// STOP
	Runnable STOP = new Runnable() {
		@Override
		public void run() {
			// show result
			show_section(R.id.result);
			h.removeCallbacks(MOVE);

			// save score
			if (score > sp.getInt("score", 0)) {
				ed.putInt("score", score);
				ed.commit();
			}

			// show score
			((TextView) findViewById(R.id.txt_result)).setText(getString(R.string.score) + " " + score);
			((TextView) findViewById(R.id.txt_high_result)).setText(getString(R.string.high_score) + " " + sp.getInt("score", 0));

			// save score to leaderboard
			if (getApiClient().isConnected()) {
				Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard_id), sp.getInt("score", 0));
			}

			// sound
			if (!sp.getBoolean("mute", false) && isForeground)
				sndpool.play(snd_result, 0.5f, 0.5f, 0, 0, 1);

			// AdMob Interstitial
			add_admob_interstitial();
		}
	};

	// onClick
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_start:
		case R.id.btn_start2:
			START();
			break;
		case R.id.btn_home:
			show_section(R.id.main);
			break;
		case R.id.btn_exit:
			finish();
			break;
		case R.id.btn_sound:
			if (sp.getBoolean("mute", false)) {
				ed.putBoolean("mute", false);
				mp.setVolume(0.5f, 0.5f);
				((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_mute));
			} else {
				ed.putBoolean("mute", true);
				mp.setVolume(0, 0);
				((Button) findViewById(R.id.btn_sound)).setText(getString(R.string.btn_sound));
			}
			ed.commit();
			break;
		case R.id.btn_play:
			if (((ToggleButton) v).isChecked())
				game_paused = false;
			else
				game_paused = true;
			break;
		case R.id.btn_leaderboard:
			// show leaderboard
			show_leaderboard = true;
			if (getApiClient().isConnected())
				onSignInSucceeded();
			else
				beginUserInitiatedSignIn();
			break;
		case R.id.btn_sign:
			// Google sign in/out
			if (getApiClient().isConnected()) {
				signOut();
				onSignInFailed();
			} else
				beginUserInitiatedSignIn();
			break;
		}
	}

	@Override
	public void onBackPressed() {
		switch (current_section) {
		case R.id.main:
			super.onBackPressed();
			break;
		case R.id.result:
			show_section(R.id.main);
			break;
		case R.id.game:
			show_section(R.id.main);
			h.removeCallbacks(MOVE);
			h.removeCallbacks(STOP);
			break;
		}
	}

	// show_section
	void show_section(int section) {
		current_section = section;
		findViewById(R.id.main).setVisibility(View.GONE);
		findViewById(R.id.game).setVisibility(View.GONE);
		findViewById(R.id.result).setVisibility(View.GONE);
		findViewById(current_section).setVisibility(View.VISIBLE);
	}

	@Override
	protected void onDestroy() {
		h.removeCallbacks(MOVE);
		h.removeCallbacks(STOP);
		mp.release();
		sndpool.release();

		// destroy AdMob
		if (adMob_smart != null)
			adMob_smart.destroy();

		super.onDestroy();
	}

	@Override
	protected void onPause() {
		isForeground = false;
		mp.setVolume(0, 0);
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		isForeground = true;

		if (!sp.getBoolean("mute", false) && isForeground)
			mp.setVolume(0.5f, 0.5f);
	}

	// DpToPx
	float DpToPx(float dp) {
		return (dp * Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) / 540f);
	}

	// hide_navigation_bar
	@TargetApi(Build.VERSION_CODES.KITKAT)
	void hide_navigation_bar() {
		// fullscreen mode
		if (android.os.Build.VERSION.SDK_INT >= 19) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			hide_navigation_bar();
		}
	}

	@Override
	public void onSignInSucceeded() {
		((Button) findViewById(R.id.btn_sign)).setText(getString(R.string.btn_sign_out));

		// save score to leaderboard
		if (show_leaderboard) {
			Games.Leaderboards.submitScore(getApiClient(), getString(R.string.leaderboard_id), sp.getInt("score", 0));

			// show leaderboard
			startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), getString(R.string.leaderboard_id)),
					9999);
		}

		// get score from leaderboard
		Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getApiClient(), getString(R.string.leaderboard_id),
				LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC).setResultCallback(
				new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {
					@Override
					public void onResult(final Leaderboards.LoadPlayerScoreResult scoreResult) {
						if (scoreResult != null && scoreResult.getStatus().getStatusCode() == GamesStatusCodes.STATUS_OK
								&& scoreResult.getScore() != null) {
							// save score localy
							if ((int) scoreResult.getScore().getRawScore() > sp.getInt("score", 0)) {
								ed.putInt("score", (int) scoreResult.getScore().getRawScore());
								ed.commit();
							}
						}
					}
				});

		show_leaderboard = false;
	}

	@Override
	public void onSignInFailed() {
		((Button) findViewById(R.id.btn_sign)).setText(getString(R.string.btn_sign_in));
		show_leaderboard = false;
	}

	// add_admob_smart
	void add_admob_smart() {
		if (show_admob_smart
				&& ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
			adMob_smart = new AdView(this);
			adMob_smart.setAdUnitId(getString(R.string.adMob_smart));
			adMob_smart.setAdSize(AdSize.SMART_BANNER);
			((ViewGroup) findViewById(R.id.admob)).addView(adMob_smart);
			com.google.android.gms.ads.AdRequest.Builder builder = new AdRequest.Builder();
			// builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice("4d0555dfcad9b000");
			adMob_smart.loadAd(builder.build());
		}
	}

	// add_admob_interstitial
	void add_admob_interstitial() {
		if (show_admob_interstitial
				&& ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo() != null) {
			adMob_interstitial = new InterstitialAd(this);
			adMob_interstitial.setAdUnitId(getString(R.string.adMob_interstitial));
			com.google.android.gms.ads.AdRequest.Builder builder = new AdRequest.Builder();
			// builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR).addTestDevice("4d0555dfcad9b000");
			adMob_interstitial.setAdListener(new AdListener() {
				@Override
				public void onAdLoaded() {
					super.onAdLoaded();

					if (current_section != R.id.game)
						adMob_interstitial.show();
				}
			});
			adMob_interstitial.loadAd(builder.build());
		}
	}
}