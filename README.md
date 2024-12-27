# HamCam

A Capacitor 6 compatible barcode scanner plugin, forked from @capacitor-community/barcode-scanner.

## Features

- Full Capacitor 6.x compatibility
- Barcode and QR code scanning
- Camera preview support
- Background scanning capability
- Torch control
- TypeScript support

## Installation

```bash
npm install @hamdev/capacitor-barcode-scanner
npx cap sync
```

## API

### scan()
```typescript
async scan(): Promise<{ hasContent: boolean; content: string; }>;
```
Start scanning for barcodes. Returns the content when found.

### prepare()
```typescript
async prepare(): Promise<void>;
```
Prepare the scanner and camera for use.

### hideBackground()
```typescript
async hideBackground(): Promise<void>;
```
Hide the background for scanning overlay.

### showBackground()
```typescript
async showBackground(): Promise<void>;
```
Restore the background after scanning.

### enableTorch()
```typescript
async enableTorch(): Promise<void>;
```
Enable the device torch/flashlight.

### disableTorch()
```typescript
async disableTorch(): Promise<void>;
```
Disable the device torch/flashlight.

### toggleTorch()
```typescript
async toggleTorch(): Promise<void>;
```
Toggle the device torch/flashlight.

### stopScan()
```typescript
async stopScan(): Promise<void>;
```
Stop the current scanning session.

## Usage Example

```typescript
import { BarcodeScanner } from '@hamdev/capacitor-barcode-scanner';

const startScan = async () => {
  // Check camera permission
  const granted = await BarcodeScanner.checkPermission({ force: true });
  
  if (!granted) {
    console.log('Please grant camera permission');
    return;
  }
  
  // Hide background
  await BarcodeScanner.hideBackground();
  
  const result = await BarcodeScanner.scan();
  
  if (result.hasContent) {
    console.log(result.content);
  }
};
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT License - see LICENSE file

## Credits

Based on the excellent work of the Capacitor Community Barcode Scanner plugin.
