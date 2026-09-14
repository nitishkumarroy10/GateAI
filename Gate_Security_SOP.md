# GateAI — Security Operations Procedure (SOP)
**Operational Reference & Handover Card | परिचालन संदर्भ और हैंडओवर कार्ड**

**Terminal:** Android Gate Device
**Role:** Main Entrance Guard / Supervisor

---

## 1. Vehicle Entry & Exit / वाहन प्रवेश और निकास

### 🔴 INWARD (गाड़ी अंदर)
1. **Tap "VEHICLE IN"** on the Dashboard.
2. Enter the **Vehicle Number** (e.g., RJ14 GB 1234).
3. Log the **Opening KM** reading from the dashboard.
4. Add driver notes (optional) and Submit.
   *(Vehicle is now marked as "Inside" / "अभी अंदर").*

### 🟢 OUTWARD (गाड़ी बाहर)
1. **Tap "VEHICLE OUT"** on the Dashboard.
2. Select the vehicle from the active list.
3. Enter the **Closing KM**.
4. **⚠️ SUPERVISOR OVERRIDE RULES:**
   - If `Closing KM` < `Opening KM` → System Error: Requires Supervisor PIN.
   - If Total Distance > 300 KM in a single shift → Flagged Alert: Requires Supervisor Approval.
5. Confirm Exit.

---

## 2. Visitor Management / आगंतुक प्रबंधन

### 🔴 VISITOR IN (आगंतुक प्रवेश)
1. **Tap "VISITOR IN"** on the Dashboard.
2. Enter Visitor Name, Phone Number, and Purpose of Visit.
3. Select the Host (Whom they are visiting).
4. Tap Submit to generate the Digital Gate Pass. 

### 🟢 VISITOR OUT (आगंतुक बाहर)
**Method A (Fast Checkout):**
1. Tap the **"CAMERA QR SCANNER"** icon on the Dashboard.
2. Scan the Visitor's Gate Pass QR Code.
3. Verify details and tap "Checkout".

**Method B (Manual Checkout):**
1. Tap **"VISITOR OUT"**.
2. Select the visitor from the "Currently Inside" list and tap Checkout.

---

## 3. Material & Consignments / माल और मरम्मत

### 📦 MATERIAL IN / OUT (माल अंदर / बाहर)
1. Navigate to the **"MATERIAL HUB"**.
2. To inward raw materials, tap **"MATERIAL IN"**. Enter invoice/challan number and quantity.
3. To dispatch finished goods, tap **"MATERIAL OUT"**.

### 🔧 REPAIRS (मरम्मत के लिए वापसी)
1. When sending parts for repair, select **"MATERIAL OUT"** and check the **"Returnable"** box.
2. To receive repaired parts, go to the **Material Hub**.
3. Under the **Pending Repairs** list, locate the outgoing challan.
4. Enter the returning quantity (partial returns are supported).

---

## 4. End of Shift Handover / शिफ्ट समाप्ति रिपोर्ट

**At the end of your duty shift (शिफ्ट के अंत में):**
1. Ensure the tablet is connected to Wi-Fi. Check the top bar for a **Green "Online"** dot.
2. Tap the **"Tools & Reports"** menu.
3. Open **"Gate Reports"**.
4. Tap **"Export Daily Register (CSV)"**.
5. Wait for the "Sync Successful" notification.
6. Hand over the device to the next shift guard and log out of your profile using the Role Switcher at the top right.

---

**EMERGENCY / SUPPORT (आपातकाल / सहायता):**
If the app shows a Red "Offline" dot for more than 1 hour, or if you encounter scanner issues, contact the IT Admin immediately.
*Device requires active Camera and Internet permissions to function correctly.*
