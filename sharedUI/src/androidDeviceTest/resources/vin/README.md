# VIN acceptance images

Drop the four real VIN-label photos here, named exactly:

| File | Label | Why it is in the set |
|---|---|---|
| `ktm.png` | KTM motorcycle frame sticker | Clean text VIN whose ISO 3779 check digit is valid |
| `swift_caravan.webp` | Swift caravan plate | Check-digit position holds a letter, so the VIN is only found if the check digit ranks rather than filters |
| `toyota_au_rotated.webp` | Toyota Australia compliance plate | Text runs bottom-to-top; only found because the still path retries all four rotations |
| `nissan_canada_1990.webp` | 1990 Nissan Canadian compliance label | No readable 17-character text anywhere — the VIN exists only in the Code 39 barcode |

`VinRecognitionDeviceTest` fails with an explicit message naming the missing file if any is absent.
