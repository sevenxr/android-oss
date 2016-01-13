package com.kickstarter.libs;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.kickstarter.R;
import com.kickstarter.libs.transformations.CircleTransformation;
import com.kickstarter.libs.transformations.CropSquareTransformation;
import com.kickstarter.libs.utils.PlayServicesUtils;
import com.kickstarter.models.pushdata.Activity;
import com.kickstarter.models.pushdata.GCM;
import com.kickstarter.services.apiresponses.PushNotificationEnvelope;
import com.kickstarter.services.gcm.RegisterService;
import com.kickstarter.services.gcm.UnregisterService;
import com.kickstarter.ui.IntentKey;
import com.kickstarter.ui.activities.ProjectActivity;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.IOException;

import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

public class PushNotifications {
  @ForApplication protected final Context context;
  protected PublishSubject<PushNotificationEnvelope> notifications = PublishSubject.create();
  protected CompositeSubscription subscriptions = new CompositeSubscription();

  public PushNotifications(@ForApplication final Context context) {
    this.context = context;
  }

  public void initialize() {
    subscriptions.add(notifications
      .filter(PushNotificationEnvelope::isFriendFollow)
      .observeOn(Schedulers.newThread())
      .subscribe(this::displayNotificationFromFriendFollowActivity));

    subscriptions.add(notifications
      .filter(PushNotificationEnvelope::isProjectActivity)
      .observeOn(Schedulers.newThread())
      .subscribe(this::displayNotificationFromProjectActivity));

    subscriptions.add(notifications
      .filter(PushNotificationEnvelope::isProjectReminder)
      .observeOn(Schedulers.newThread())
      .subscribe(this::displayNotificationFromProjectReminder));

    subscriptions.add(notifications
      .filter(PushNotificationEnvelope::isProjectUpdateActivity)
      .observeOn(Schedulers.newThread())
      .subscribe(this::displayNotificationFromUpdateActivity));

    registerDevice();
  }

  public void registerDevice() {
    if (!PlayServicesUtils.isAvailable(context)) {
      return;
    }

    context.startService(new Intent(context, RegisterService.class));
  }

  public void unregisterDevice() {
    if (!PlayServicesUtils.isAvailable(context)) {
      return;
    }

    context.startService(new Intent(context, UnregisterService.class));
  }

  public void add(@NonNull final PushNotificationEnvelope envelope) {
    notifications.onNext(envelope);
  }

  private void displayNotificationFromFriendFollowActivity(@NonNull final PushNotificationEnvelope envelope) {
    final Activity activity = envelope.activity();
    final GCM gcm = envelope.gcm();

    // TODO: intent
    final Notification notification = notificationBuilder(gcm.title(), gcm.alert())
      .setLargeIcon(fetchBitmap(activity.userPhoto(), true))
      .build();
    notificationManager().notify(envelope.signature(), notification);
  }

  private void displayNotificationFromProjectActivity(@NonNull final PushNotificationEnvelope envelope) {
    final GCM gcm = envelope.gcm();

    final Activity activity = envelope.activity();
    if (activity == null) { return; }
    final String projectPhoto = activity.projectPhoto();
    if (projectPhoto == null) { return; }
    final Long projectId = activity.projectId();
    if (projectId == null) { return; }

    final Notification notification = notificationBuilder(gcm.title(), gcm.alert())
      .setLargeIcon(fetchBitmap(projectPhoto, false))
      .setContentIntent(projectContentIntent(projectId, envelope.signature()))
      .build();
    notificationManager().notify(envelope.signature(), notification);
  }

  private void displayNotificationFromProjectReminder(@NonNull final PushNotificationEnvelope envelope) {
    final GCM gcm = envelope.gcm();

    final Notification notification = notificationBuilder(gcm.title(), gcm.alert())
      .setLargeIcon(fetchBitmap(envelope.project().photo(), false))
      .setContentIntent(projectContentIntent(envelope.project().id(), envelope.signature()))
      .build();

    notificationManager().notify(envelope.signature(), notification);
  }

  private void displayNotificationFromUpdateActivity(@NonNull final PushNotificationEnvelope envelope) {
    final Activity activity = envelope.activity();
    final GCM gcm = envelope.gcm();

    // TODO: Intent
    final Notification notification = notificationBuilder(gcm.title(), gcm.alert())
      .setLargeIcon(fetchBitmap(activity.projectPhoto(), false))
      .build();
    notificationManager().notify(envelope.signature(), notification);
  }

  private @NonNull NotificationCompat.Builder notificationBuilder(@NonNull final String title, @NonNull final String text) {
    return new NotificationCompat.Builder(context)
      .setSmallIcon(R.drawable.ic_kickstarter_k)
      .setColor(context.getResources().getColor(R.color.green))
      .setContentText(text)
      .setContentTitle(title)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
      .setAutoCancel(true);
  }

  private @NonNull PendingIntent projectContentIntent(@NonNull final Long projectId, final int uniqueNotificationId) {
    // TODO: This is still WIP
    final Intent intent = new Intent(context, ProjectActivity.class)
      .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)
      .putExtra(IntentKey.PROJECT_PARAM, projectId.toString());

    // TODO: Check the flags!
    return PendingIntent.getActivity(context, uniqueNotificationId, intent, PendingIntent.FLAG_CANCEL_CURRENT);
  }

  private @Nullable Bitmap fetchBitmap(@NonNull final String url, final boolean transformIntoCircle) {
    try {
      RequestCreator requestCreator = Picasso.with(context).load(url).transform(new CropSquareTransformation());
      if (transformIntoCircle) {
        requestCreator = requestCreator.transform(new CircleTransformation());
      }
      return requestCreator.get();
    } catch (IOException e) {
      Timber.e("Failed to load large icon: %s",  e);
      return null;
    }
  }

  private @NonNull NotificationManager notificationManager() {
    return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
  }
}
