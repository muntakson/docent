#!/usr/bin/env python3
"""
AMR API Proxy Server
Forwards requests from the web UI to the AMR navigation system
to avoid CORS issues
Also provides Google Cloud TTS endpoint
"""

from flask import Flask, request, jsonify, Response
from flask_cors import CORS
import requests
import logging
import base64
import json

app = Flask(__name__)
CORS(app)  # Enable CORS for all routes

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# AMR Navigation System IP (can be changed via environment variable)
import os
AMR_IP = os.environ.get('AMR_IP', '192.168.219.42')
AMR_BASE_URL = f'http://{AMR_IP}'

# Google Cloud TTS API Key (set via environment variable or here)
GOOGLE_TTS_API_KEY = os.environ.get('GOOGLE_TTS_API_KEY', '')

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({'status': 'ok', 'amr_ip': AMR_IP, 'tts_enabled': bool(GOOGLE_TTS_API_KEY)})

@app.route('/api/tts', methods=['POST'])
def text_to_speech():
    """
    Google Cloud Text-to-Speech endpoint
    Expects JSON: { "text": "안녕하세요", "voice": "ko-KR-Wavenet-A" }
    Returns: audio/mp3
    """
    if not GOOGLE_TTS_API_KEY:
        return jsonify({'error': 'TTS API key not configured'}), 503

    data = request.json
    text = data.get('text', '')
    voice_name = data.get('voice', 'ko-KR-Wavenet-A')  # Default Korean female voice
    speaking_rate = data.get('rate', 1.0)
    pitch = data.get('pitch', 0.0)

    if not text:
        return jsonify({'error': 'No text provided'}), 400

    # Google Cloud TTS API request
    tts_url = f'https://texttospeech.googleapis.com/v1/text:synthesize?key={GOOGLE_TTS_API_KEY}'

    payload = {
        'input': {'text': text},
        'voice': {
            'languageCode': 'ko-KR',
            'name': voice_name
        },
        'audioConfig': {
            'audioEncoding': 'MP3',
            'speakingRate': speaking_rate,
            'pitch': pitch
        }
    }

    try:
        response = requests.post(tts_url, json=payload, timeout=30)
        response.raise_for_status()

        result = response.json()
        audio_content = result.get('audioContent', '')

        if audio_content:
            # Decode base64 and return as audio
            audio_data = base64.b64decode(audio_content)
            return Response(audio_data, mimetype='audio/mp3')
        else:
            return jsonify({'error': 'No audio content returned'}), 500

    except requests.exceptions.RequestException as e:
        logger.error(f'TTS API error: {str(e)}')
        return jsonify({'error': str(e)}), 500

@app.route('/api/tts/voices', methods=['GET'])
def list_voices():
    """List available Korean voices"""
    voices = [
        {'name': 'ko-KR-Wavenet-A', 'gender': 'FEMALE', 'description': '여성 (Wavenet A)'},
        {'name': 'ko-KR-Wavenet-B', 'gender': 'FEMALE', 'description': '여성 (Wavenet B)'},
        {'name': 'ko-KR-Wavenet-C', 'gender': 'MALE', 'description': '남성 (Wavenet C)'},
        {'name': 'ko-KR-Wavenet-D', 'gender': 'MALE', 'description': '남성 (Wavenet D)'},
        {'name': 'ko-KR-Neural2-A', 'gender': 'FEMALE', 'description': '여성 (Neural2 A) - 고품질'},
        {'name': 'ko-KR-Neural2-B', 'gender': 'FEMALE', 'description': '여성 (Neural2 B) - 고품질'},
        {'name': 'ko-KR-Neural2-C', 'gender': 'MALE', 'description': '남성 (Neural2 C) - 고품질'},
        {'name': 'ko-KR-Standard-A', 'gender': 'FEMALE', 'description': '여성 (Standard A)'},
        {'name': 'ko-KR-Standard-B', 'gender': 'FEMALE', 'description': '여성 (Standard B)'},
        {'name': 'ko-KR-Standard-C', 'gender': 'MALE', 'description': '남성 (Standard C)'},
        {'name': 'ko-KR-Standard-D', 'gender': 'MALE', 'description': '남성 (Standard D)'},
    ]
    return jsonify({'voices': voices, 'tts_enabled': bool(GOOGLE_TTS_API_KEY)})

@app.route('/api/<path:endpoint>', methods=['GET', 'POST', 'DELETE'])
def proxy(endpoint):
    """
    Proxy all API requests to the AMR navigation system
    """
    url = f'{AMR_BASE_URL}/{endpoint}'

    try:
        # Forward the request to the AMR
        if request.method == 'GET':
            response = requests.get(url, timeout=5)
        elif request.method == 'POST':
            response = requests.post(
                url,
                json=request.json,
                headers={'Content-Type': 'application/json'},
                timeout=5
            )
        elif request.method == 'DELETE':
            # DELETE requests may have array body (e.g., ["pointName"])
            data = request.get_data(as_text=True)
            response = requests.delete(
                url,
                data=data,
                headers={'Content-Type': 'application/json'},
                timeout=5
            )
        else:
            return jsonify({'error': 'Method not allowed'}), 405

        # Log the request
        logger.info(f'{request.method} /{endpoint} -> {url} [{response.status_code}]')

        # Return the response
        try:
            return jsonify(response.json()), response.status_code
        except:
            return response.text, response.status_code

    except requests.exceptions.Timeout:
        logger.error(f'Timeout connecting to AMR at {url}')
        return jsonify({'error': 'AMR connection timeout', 'url': url}), 504
    except requests.exceptions.ConnectionError:
        logger.error(f'Connection error to AMR at {url}')
        return jsonify({'error': 'Cannot connect to AMR', 'url': url}), 503
    except Exception as e:
        logger.error(f'Error proxying request: {str(e)}')
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    print(f'Starting AMR Proxy Server...')
    print(f'AMR Navigation System: {AMR_BASE_URL}')
    print(f'Proxy listening on http://0.0.0.0:5020')
    app.run(host='0.0.0.0', port=5020, debug=False)
