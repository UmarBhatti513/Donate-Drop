const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const sgMail = require("@sendgrid/mail");
const SENDGRID_KEY = functions.config().sendgrid?.key;
const FROM_EMAIL = functions.config().sendgrid?.from || "donatedropapp@gmail.com";
const FROM_NAME = functions.config().sendgrid?.from_name || "DonateDrop";

if (!SENDGRID_KEY) {
  console.warn("SendGrid key not configured. Set with `firebase functions:config:set sendgrid.key=...`");
} else {
  sgMail.setApiKey(SENDGRID_KEY);
}

/**
 * Trigger: when a request is updated and its Status changes to "pending"
 */
exports.onRequestClaimed = functions.firestore
  .document("Blood Requests/{requestId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data() || {};
    const after  = change.after.data()  || {};
    const requestId = context.params.requestId;

    const beforeStatus = (before["Status"] || "").toString().trim().toLowerCase();
    const afterStatus  = (after["Status"]  || "").toString().trim().toLowerCase();

    // only act when status flips to pending
    if (beforeStatus === afterStatus) return null;
    if (afterStatus !== "pending") return null;

    // gather data
    const createdByUid = after["Created By"] || after["createdBy"] || null;
    let requesterEmail = after["Requester Email"] || after["RequesterEmail"] || after["Requester_Email"] || null;
    const receiverName = after["Receiver's Name"] || after["Receiver Name"] || after["receiverName"] || "Receiver";
    const hospitalAddr = after["Hospital Address"] || after["hospitalAddress"] || "";
    const bloodType = after["Blood Type"] || "";
    const qty = after["Quantity"] || "";
    const urgency = after["Urgency Level"] || after["Urgency"] || "";
    const responderName = after["Responder Name"] || after["ResponderName"] || after["responderName"] || "Responder";
    const responderPhone = after["Responder Phone"] || after["Responder Phone"] || after["ResponderPhone"] || "";

    // Resolve requesterEmail if not on doc
    if (!requesterEmail && createdByUid) {
      try {
        const userDoc = await admin.firestore().collection("users").doc(createdByUid).get();
        requesterEmail = userDoc.exists ? (userDoc.get("email") || userDoc.get("Email") || userDoc.get("emailAddress")) : null;
      } catch (err) {
        console.error("Failed to read users/{uid} for email:", err);
      }
    }

    if (!requesterEmail) {
      console.log(`No requester email for request ${requestId} — skipping email.`);
      return null;
    }

    // Build email
    const subject = `Someone responded to your blood request (${bloodType}) — DonateDrop`;
    const text = `
Hi ${receiverName || ""},

Good news — someone has responded to your blood request (ID: ${requestId}).

Details:
- Responder: ${responderName}
- Phone: ${responderPhone || "Not provided"}
- Blood Type: ${bloodType}
- Quantity: ${qty}
- Urgency: ${urgency}
- Hospital: ${hospitalAddr}

Please contact the responder as soon as possible. Thank you for using DonateDrop.

— DonateDrop Team
    `;

    const html = `
<p>Hi <strong>${receiverName}</strong>,</p>
<p><strong>Good news</strong> — someone has responded to your blood request (ID: <code>${requestId}</code>).</p>
<ul>
  <li><strong>Responder:</strong> ${responderName}</li>
  <li><strong>Phone:</strong> ${responderPhone || "Not provided"}</li>
  <li><strong>Blood Type:</strong> ${bloodType}</li>
  <li><strong>Quantity:</strong> ${qty}</li>
  <li><strong>Urgency:</strong> ${urgency}</li>
  <li><strong>Hospital:</strong> ${hospitalAddr}</li>
</ul>
<p>Please contact the responder as soon as possible.</p>
<p>Thanks,<br/>DonateDrop Team</p>
`;

    const msg = {
      to: requesterEmail,
      from: {
        email: FROM_EMAIL,
        name: FROM_NAME
      },
      subject,
      text,
      html
    };

    try {
      console.log("Sending email to", requesterEmail);
      if (!SENDGRID_KEY) {
        console.warn("SendGrid not configured; skipping send.");
        return null;
      }
      await sgMail.send(msg);
      console.log("Email sent for request", requestId);
      return null;
    } catch (err) {
      console.error("SendGrid error:", err);
      return null;
    }
  });
