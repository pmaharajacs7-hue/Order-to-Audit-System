# Demo guide: run the benchmark in the UI, then record the video

## Part A. Run the benchmark in the UI

### A0. Start clean
1. Stop any running backend, then start it fresh so the database is empty: `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
2. Start the UI: `cd frontend && npm install && npm run dev`, then open http://localhost:5173
3. Password for every demo user is `Passw0rd!`.

### A1. Master data (login: `admin`, page **Master data**)
Use the tabs along the top.

| Tab | Add this |
|---|---|
| Suppliers | Name `Annapurna Flour Mills` |
| Materials | `Flour` (Raw material, kg), `Oil` (Raw material, litre), `Spice mix` (Raw material, kg), `Pouch` (Packaging, piece), `Carton` (Packaging, piece) |
| Recipes (BOM) | Name `Murukku BOM`, finished snack per batch `40` kg. Lines (basis per batch): Flour 40, Oil 10, Spice mix 1.2, Pouch 200, Carton 10 |
| Products | `Murukku 200 g`: SKU `MUR200`, pack 200 g, price 50, HSN 19059090, GST 5, recipe = Murukku BOM. Then `Murukku 500 g`: SKU `MUR500`, pack 500 g, price 120, same recipe |
| Customers | `Retailer A`, GSTIN `33ABCDE1234F1Z5`, state code `33`. Then `Distributor B`, state code `33` |

Check: the recipe list shows both products can share one recipe (expected 200 packets of 200 g, or 80 of 500 g).

### A2. Opening stock (admin, page **Inventory**, form "Opening stock / correction")
Record each as type Opening: Flour 100 @ 42, Oil 30 @ 140, Spice mix 5 @ 200, Pouch 500 @ 2.5, Carton 30 @ 15, and finished goods `Murukku 200 g` 50 @ 0.

### A3. Purchase (admin, page **Purchases**)
Supplier Annapurna Flour Mills, material Flour, 60 kg at ₹45. Open **Inventory**, select Flour: the ledger shows 100 then 160.

### A4. Production (login `supervisor` or stay as admin, page **Production**)
Product `Murukku 200 g`, batches 1, actual output 188, good packets 188, damaged 6, labour 1200, gas/power 600. Material actuals: Flour 40, Oil 10.5, Spice mix 1.2, Pouch 194, Carton 9.

Expected on screen:
- Yield −6.00%, flagged. Oil +5.00%, flagged.
- Cost per packet about ₹31.14 (materials 4,055 + 1,200 + 600 over 188).
- Inventory: Flour 120 kg, Oil 19.5 L, Pouch 306, Carton 21, finished goods 238.

### A5. Order, invoice, dispatch
1. Login `salesman`, page **Orders**: customer Retailer A, product Murukku 200 g, 150 packets, submit. The salesman sees only the Orders page.
2. Login `admin`, page **Orders**: click **Approve**, then **Create invoice**, then **Dispatch**.
3. Expected: invoice total **₹7,875** (7,500 + 187.50 CGST + 187.50 SGST). Use the invoice PDF button to download it.
4. Inventory: finished goods now **88**.

### A6. Shortage check
Login `salesman`, create an order for **Distributor B**, 120 packets. As `admin`, click **Approve**. Expected: it is refused with a shortage (88 available, 32 short) and the order stays Submitted.

### A7. Payment and reports
1. Admin, page **Payments**: invoice for Retailer A, amount 4000, mode UPI. Expected balance **₹3,875**.
2. Try 3875.01 on the same invoice: refused as an overpayment.
3. Login `accountant`, page **Reports**: Outstanding shows Retailer A invoiced 7,875, paid 4,000, outstanding 3,875. Also open GST summary and Profitability, and download one report as CSV.
4. Login `owner`: the Dashboard shows invoiced, collected, outstanding and the flagged production run.

### A8. Role checks (these are the 403 cases)
- `salesman`: no Reports, Payments or Production pages in the menu.
- `accountant`: Payments can be viewed but recording a payment is refused (403).
- In Postman, "Salesman cannot see reports (403)" and "Accountant cannot record payments (403)" both pass.

### A9. API benchmark
Restart the backend (fresh database), import `docs/postman_collection.json`, and run the whole collection once. All checks should pass. Do this on its own fresh database, not after the UI run above.

---

## Part B. Record the demo video (about 6–8 minutes)

### B1. Set up
1. Windows: press `Win + G` for Xbox Game Bar and click Record, or install OBS Studio (free). Mac: `Shift + Cmd + 5`. OBS gives the best quality: add a "Display Capture" source and a microphone, then click Start Recording.
2. Set the screen to 1920×1080, close other windows and notifications, and zoom the browser to 110–125% so text is readable.
3. Start from a **fresh backend database** (A0) so the numbers match your script. Have this guide open on a second screen, not in the recording.
4. Do one dry run first. Speak slowly and name each number as it appears.

### B2. Suggested script
| Time | Show | Say |
|---|---|---|
| 0:00 | Title and README | What the system does: purchase → production → stock → order → GST invoice → payment → audit |
| 0:30 | Architecture and ER diagram (`docs/er-diagram.png`) | React → Spring Boot REST + JWT → MySQL; stock is a ledger |
| 1:15 | Login as admin, Master data | One recipe shared by 200 g and 500 g products |
| 2:00 | Inventory, Purchases | Opening stock, flour 100 → 160 kg |
| 2:45 | Production (supervisor) | 188 of 200 packets, −6% yield flagged, ₹31.14 per packet |
| 3:45 | Orders: salesman places, admin approves, invoices, dispatches | ₹7,875 with CGST/SGST, stock 238 → 88; PDF invoice |
| 5:00 | Distributor B order | Shortage 32, approval blocked |
| 5:30 | Payments, then Reports as accountant | ₹4,000 paid, ₹3,875 outstanding; GST report; CSV; audit trail |
| 6:30 | Role restrictions | Salesman has no reports; 403 on forbidden calls |
| 7:00 | Postman | Run the whole collection, all green |
| 7:30 | Close | Repository link, README, design note |

### B3. After recording
1. Watch it once: audio clear, numbers readable, no passwords or personal data on screen.
2. Export as MP4. Upload to YouTube (Unlisted) or Google Drive (anyone with the link can view), or whatever the submission asks for.
3. Add the video link to the README (under a "Demo video" heading) and to the submission form.
