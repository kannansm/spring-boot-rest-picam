/**
 * 
 */
package com.mycom.rest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.hopding.jrpicam.RPiCamera;
import com.hopding.jrpicam.enums.AWB;
import com.hopding.jrpicam.enums.DRC;
import com.hopding.jrpicam.enums.Encoding;
import com.hopding.jrpicam.exceptions.FailedToRunRaspistillException;

/**
 * @author kannan
 * @since 1.0
 */
@RestController
@RequestMapping("/cam")
public class CameraController {
	private static final Logger logger = LoggerFactory.getLogger(CameraController.class);

	@Autowired
	ResourceLoader resourceLoader;

	private String imageDir = "/home/pi/apps/pictures";
	private String pictureNamePrefix = "picam-pic-";
	private String extension = ".jpg";
	private AtomicInteger count = new AtomicInteger();

	private RPiCamera piCamera = null;

	private void init() {
		// Attempt to create an instance of RPiCamera, will fail if raspistill is not
		// properly installed
		try {
			piCamera = new RPiCamera(imageDir);
			piCamera.setAWB(AWB.AUTO) // Change Automatic White Balance setting to automatic
					.setRotation(90)
					.setDRC(DRC.OFF) // Turn off Dynamic Range Compression
					.setContrast(100) // Set maximum contrast
					.setSharpness(100) // Set maximum sharpness
					.setQuality(100) // Set maximum quality
					.setTimeout(1000) // Wait 1 second to take the image
					.turnOnPreview() // Turn on image preview
					.setEncoding(Encoding.JPG); // Change encoding of images to JPG
			logger.debug("Initialized RPiCamera with raspistill successfully: '{}'", piCamera);
		} catch (FailedToRunRaspistillException e) {
			logger.error("Failed to initialize RPiCamera with Raspistill: ", e);
		}
	}

	@ResponseBody
	@GetMapping(value = "/capture", produces = { "application/json;charset=UTF-8" })
	public Map<String, Object> takePicture(Model model) {
		Map<String, Object> response = capturePicture();

		logger.debug("Returning '{}'", response);
		return response;
	}

	@ResponseBody
	@GetMapping(value = "/capture/show", produces = { MediaType.IMAGE_JPEG_VALUE })
	public ResponseEntity<byte[]> takePictureAndShow(Model model) {
		Map<String, Object> response = capturePicture();

		String picName = (String) response.get("PictureName");
		String resource = imageDir + "/" + response.get("PictureName");

		logger.debug("Returning '{}'", response);

		ResponseEntity<byte[]> responseEntity = createResponseEntity(picName, resource);
		return responseEntity;
	}

	@GetMapping(value = "/show/{number}", produces = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<byte[]> getImageAsResponseEntity(@PathVariable("number") final String number) {
		String picName = pictureNamePrefix + number + extension;
		String resource = imageDir + "/" + picName;
		logger.debug("Loading resource: '{}'", resource);

		ResponseEntity<byte[]> responseEntity = createResponseEntity(picName, resource);
		return responseEntity;
	}

	private Map<String, Object> capturePicture() {
		int current = count.getAndIncrement();
		if (current > 25) {
			count.set(0);
			current = count.get();
			logger.debug("Resetting count to: '{}'", current);
		}
		String picName = pictureNamePrefix + current + extension;

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("PictureName", picName);

		if (piCamera == null) {
			init();
		}

		File image = null;
		try {
			image = piCamera.takeStill(picName, 650, 650);
			logger.info("New PNG image saved to: {}", image.getAbsolutePath());
			response.put("count", current);
			response.put("Result", "success");
		} catch (IOException | InterruptedException e) {
			logger.error("Failed to capture image: ", e);
			response.put("count", count.decrementAndGet());
			response.put("Result", "failed");
		}
		return response;
	}

	private ResponseEntity<byte[]> createResponseEntity(String picName, String resource) {
		ResponseEntity<byte[]> responseEntity;
		HttpHeaders headers = new HttpHeaders();
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("PictureName", picName);

		// InputStream in = getClass().getResourceAsStream(resource);
		Resource res = resourceLoader.getResource("file://" + resource);
		logger.debug("Loaded resource: '{}'", res);
		byte[] media = null;

		try {
			InputStream in = res.getInputStream();
			media = IOUtils.toByteArray(in);
			responseEntity = new ResponseEntity<>(media, headers, HttpStatus.OK);
			response.put("Result", "success");
		} catch (IOException e) {
			logger.error("Failed to load image: ", e);
			responseEntity = new ResponseEntity<>(media, headers, HttpStatus.NOT_FOUND);
			response.put("Result", "Failed due to IOException");
		}

		headers.setCacheControl(CacheControl.noCache().getHeaderValue());

		logger.debug("Result: '{}'", response);
		return responseEntity;
	}
}
