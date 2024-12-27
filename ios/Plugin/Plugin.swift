import Foundation
import Capacitor
import AVFoundation
import MLKitBarcodeScanning
import MLKitVision

@objc(BarcodeScannerPlugin)
public class BarcodeScannerPlugin: CAPPlugin, AVCaptureMetadataOutputObjectsDelegate {
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var captureSession: AVCaptureSession?
    private var scanner: BarcodeScanner?
    private var isScanning = false
    private var savedCall: CAPPluginCall?
    private var overlayView: UIView?
    
    override public func load() {
        scanner = BarcodeScanner.barcodeScanner()
    }
    
    @objc func prepare(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.setupUI()
            call.resolve()
        }
    }
    
    @objc func hideBackground(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.bridge?.viewController?.view.backgroundColor = .clear
            call.resolve()
        }
    }
    
    @objc func showBackground(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.bridge?.viewController?.view.backgroundColor = .white
            call.resolve()
        }
    }
    
    @objc func startScan(_ call: CAPPluginCall) {
        checkPermission { [weak self] granted in
            if granted {
                self?.savedCall = call
                self?.startScanning()
            } else {
                call.reject("Camera permission not granted")
            }
        }
    }
    
    @objc func stopScan(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.stopScanning()
            call.resolve()
        }
    }
    
    private func setupUI() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            self.overlayView = UIView(frame: self.bridge?.viewController?.view.bounds ?? .zero)
            self.overlayView?.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            
            if let overlayView = self.overlayView {
                self.bridge?.viewController?.view.addSubview(overlayView)
            }
        }
    }
    
    private func startScanning() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            let captureSession = AVCaptureSession()
            self.captureSession = captureSession
            
            guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else {
                self.savedCall?.reject("Failed to get camera device")
                return
            }
            
            let videoInput: AVCaptureDeviceInput
            
            do {
                videoInput = try AVCaptureDeviceInput(device: videoCaptureDevice)
            } catch {
                self.savedCall?.reject("Failed to create camera input: \(error.localizedDescription)")
                return
            }
            
            if captureSession.canAddInput(videoInput) {
                captureSession.addInput(videoInput)
            } else {
                self.savedCall?.reject("Failed to add camera input to session")
                return
            }
            
            let output = AVCaptureVideoDataOutput()
            output.setSampleBufferDelegate(self, queue: DispatchQueue.global(qos: .userInitiated))
            
            if captureSession.canAddOutput(output) {
                captureSession.addOutput(output)
            } else {
                self.savedCall?.reject("Failed to add camera output")
                return
            }
            
            let previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
            self.previewLayer = previewLayer
            
            DispatchQueue.main.async {
                previewLayer.frame = self.overlayView?.bounds ?? .zero
                previewLayer.videoGravity = .resizeAspectFill
                self.overlayView?.layer.addSublayer(previewLayer)
            }
            
            self.isScanning = true
            captureSession.startRunning()
        }
    }
    
    private func stopScanning() {
        isScanning = false
        captureSession?.stopRunning()
        previewLayer?.removeFromSuperlayer()
        overlayView?.removeFromSuperview()
        savedCall = nil
    }
    
    private func checkPermission(completion: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            completion(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                completion(granted)
            }
        default:
            completion(false)
        }
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
extension BarcodeScannerPlugin: AVCaptureVideoDataOutputSampleBufferDelegate {
    public func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard isScanning,
              let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }
        
        let image = VisionImage(buffer: sampleBuffer)
        image.orientation = imageOrientation()
        
        scanner?.process(image) { [weak self] barcodes, error in
            guard let self = self,
                  error == nil,
                  let barcode = barcodes?.first else {
                return
            }
            
            guard let rawValue = barcode.rawValue else {
                return
            }
            
            DispatchQueue.main.async {
                self.isScanning = false
                let result = JSObject()
                result["hasContent"] = true
                result["content"] = rawValue
                self.savedCall?.resolve(result)
                self.stopScanning()
            }
        }
    }
    
    private func imageOrientation() -> UIImage.Orientation {
        guard let connection = captureSession?.connections.first else {
            return .up
        }
        
        switch connection.videoOrientation {
        case .portrait:
            return .right
        case .portraitUpsideDown:
            return .left
        case .landscapeLeft:
            return .up
        case .landscapeRight:
            return .down
        @unknown default:
            return .up
        }
    }
}
