const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

// Cloud Function that triggers when a new notification is created
exports.sendWalkWithMeNotification = functions.firestore
    .document('Notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        try {
            const notificationData = snap.data();
            const notificationId = context.params.notificationId;
            
            console.log('New notification created:', notificationId);
            console.log('Notification data:', notificationData);
            
            // Only process walk_with_me_request notifications
            if (notificationData.type !== 'walk_with_me_request') {
                console.log('Not a walk_with_me_request, skipping');
                return null;
            }
            
            // Check if we have the required data
            if (!notificationData.recipientFcmToken || !notificationData.senderName) {
                console.error('Missing required notification data');
                return null;
            }
            
            // Create the push notification message
            const message = {
                token: notificationData.recipientFcmToken,
                notification: {
                    title: 'Urgent: Walk With Me Request',
                    body: `${notificationData.senderName} needs someone to walk with them at ${notificationData.location || 'their location'}`
                },
                data: {
                    type: 'walk_with_me_request',
                    notificationId: notificationId,
                    sosRequestId: notificationData.sosRequestId || '',
                    senderName: notificationData.senderName || '',
                    senderUserId: notificationData.senderUserId || '',
                    location: notificationData.location || ''
                },
                android: {
                    priority: 'high',
                    notification: {
                        channelId: 'urgent_notifications',
                        priority: 'max',
                        defaultSound: true,
                        defaultVibrateTimings: true
                    }
                },
                apns: {
                    payload: {
                        aps: {
                            sound: 'default',
                            badge: 1
                        }
                    }
                }
            };
            
            console.log('Sending message to token:', notificationData.recipientFcmToken);
            
            // Send the message
            const response = await admin.messaging().send(message);
            console.log('Successfully sent message:', response);
            
            // Update the notification document to mark it as sent
            await snap.ref.update({
                pushNotificationSent: true,
                pushNotificationSentAt: admin.firestore.FieldValue.serverTimestamp(),
                messagingResponse: response
            });
            
            return response;
            
        } catch (error) {
            console.error('Error sending notification:', error);
            
            // Update the notification document to mark the error
            await snap.ref.update({
                pushNotificationSent: false,
                pushNotificationError: error.message,
                pushNotificationSentAt: admin.firestore.FieldValue.serverTimestamp()
            });
            
            throw error;
        }
    });

// Optional: Cloud Function to handle notification responses
exports.handleWalkWithMeResponse = functions.firestore
    .document('Notifications/{notificationId}')
    .onUpdate(async (change, context) => {
        try {
            const before = change.before.data();
            const after = change.after.data();
            
            // Only process if status changed from pending to accepted/declined
            if (before.status === 'pending' && (after.status === 'accepted' || after.status === 'declined')) {
                console.log('Notification response received:', after.status);
                
                // Get the original SOS request
                const sosDoc = await admin.firestore().collection('SOS').doc(after.sosRequestId).get();
                if (!sosDoc.exists) {
                    console.error('SOS request not found');
                    return null;
                }
                
                const sosData = sosDoc.data();
                
                // Send response notification back to the original requester
                const responseMessage = {
                    token: sosData.requesterFcmToken, // You'll need to store this when creating SOS
                    notification: {
                        title: after.status === 'accepted' ? 'Walk Request Accepted!' : 'Walk Request Response',
                        body: after.status === 'accepted' 
                            ? `${after.responderName || 'Someone'} will walk with you! They should contact you soon.`
                            : `${after.responderName || 'Someone'} declined your request. Others might still respond.`
                    },
                    data: {
                        type: 'walk_with_me_response',
                        response: after.status,
                        responderName: after.responderName || '',
                        sosRequestId: after.sosRequestId
                    },
                    android: {
                        priority: 'high'
                    }
                };
                
                if (sosData.requesterFcmToken) {
                    const response = await admin.messaging().send(responseMessage);
                    console.log('Response notification sent:', response);
                }
            }
            
            return null;
        } catch (error) {
            console.error('Error handling response:', error);
            throw error;
        }
    });