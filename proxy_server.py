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

# Zone configuration file path
ZONES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'zones.json')

# Settings configuration file path
SETTINGS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'settings.json')

# Default settings
DEFAULT_SETTINGS = {
    'amr_ip': '192.168.219.42'
}

def load_settings():
    """Load settings from file, or return defaults if file doesn't exist"""
    try:
        if os.path.exists(SETTINGS_FILE):
            with open(SETTINGS_FILE, 'r', encoding='utf-8') as f:
                settings = json.load(f)
                # Merge with defaults to ensure all keys exist
                return {**DEFAULT_SETTINGS, **settings}
    except Exception as e:
        logger.error(f'Error loading settings: {e}')
    return DEFAULT_SETTINGS.copy()

def save_settings(settings):
    """Save settings to file"""
    try:
        with open(SETTINGS_FILE, 'w', encoding='utf-8') as f:
            json.dump(settings, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        logger.error(f'Error saving settings: {e}')
        return False

def get_amr_ip():
    """Get current AMR IP from settings"""
    settings = load_settings()
    return settings.get('amr_ip', DEFAULT_SETTINGS['amr_ip'])

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({'status': 'ok', 'amr_ip': get_amr_ip(), 'tts_enabled': bool(GOOGLE_TTS_API_KEY)})

@app.route('/api/settings', methods=['GET', 'PUT'])
def settings_endpoint():
    """Get or update application settings (AMR IP, etc.)"""
    if request.method == 'GET':
        settings = load_settings()
        return jsonify(settings)
    elif request.method == 'PUT':
        try:
            new_settings = request.json
            if not isinstance(new_settings, dict):
                return jsonify({'error': 'Invalid settings format, expected object'}), 400

            # Load existing settings and update with new values
            settings = load_settings()
            settings.update(new_settings)

            if save_settings(settings):
                logger.info(f'Settings saved: {settings}')
                return jsonify({'success': True, 'message': 'Settings saved successfully', 'settings': settings})
            else:
                return jsonify({'error': 'Failed to save settings'}), 500
        except Exception as e:
            logger.error(f'Error saving settings: {e}')
            return jsonify({'error': str(e)}), 500

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

# Default zones for the docent app
DEFAULT_ZONES = [
    {
        'id': 'zone1',
        'name': '웰컴 존',
        'pointName': '',
        'speech': '안녕 나는 서희봇이야! 이천 서희도서관 맘대로 에이플러스 놀이터에 온걸을 환영해! 미래에서 가장 중요한 에이아이와 반도체에 대해서 즐겁게 알아보는 시간을 가져볼까?',
        'videoUrl': 'https://storage.iotok.org/download/23/docentbot.mp4'
    },
    {
        'id': 'zone2',
        'name': '미디어 존',
        'pointName': '',
        'speech': '앞으로 우리가 살아갈 미래에 가장 중요한 것이 인공지능과 반도체야! 우리 생활속에서 어떻게 사용되는지 함께 알아보고 우리가 만들어갈 미래에 대해서도 함께 이야기해볼까?',
        'videoUrl': ''
    },
    {
        'id': 'zone3',
        'name': '인터렉티브 존',
        'pointName': '',
        'speech': '인공지능과 반도체가 어떻게 작동되는지 우리 체험놀이를 통해서 알아볼까?',
        'videoUrl': ''
    },
    {
        'id': 'zone4',
        'name': '홀로그램 존',
        'pointName': '',
        'speech': '우와! 다양한 물고기 로봇을 만들고 있네? 이런 로봇에 들어가는 인공지능 반도체는 어떤 모습일까? 반도체가 어떻게 생겼는지 함께 알아볼까? 렛츠고!',
        'videoUrl': ''
    },
    {
        'id': 'zone5',
        'name': '미래역량체험 존',
        'pointName': '',
        'speech': '여기는 마음껏 뛰어놀고 함께 미래역량 체험놀이터야! 놀다보면 나도모르게 미래 창의역량, 협동역량, 비판적사고역량, 의사소통능력이 생기는 곳이지!',
        'videoUrl': ''
    },
    {
        'id': 'zone6',
        'name': '메모리 존',
        'pointName': '',
        'speech': '오늘 체험은 즐거웠니? 헤어지는게 너무 아쉬워서 사진을 찍었어! 우리 소중한 추억을 함께 기억할께! 다음에 보자!',
        'videoUrl': ''
    }
]

def load_zones():
    """Load zones from file, or return defaults if file doesn't exist"""
    try:
        if os.path.exists(ZONES_FILE):
            with open(ZONES_FILE, 'r', encoding='utf-8') as f:
                zones = json.load(f)
                # Ensure all zones have videoUrl field (for backward compatibility)
                for zone in zones:
                    if 'videoUrl' not in zone:
                        zone['videoUrl'] = ''
                return zones
    except Exception as e:
        logger.error(f'Error loading zones: {e}')
    return DEFAULT_ZONES.copy()

def save_zones(zones):
    """Save zones to file"""
    try:
        with open(ZONES_FILE, 'w', encoding='utf-8') as f:
            json.dump(zones, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        logger.error(f'Error saving zones: {e}')
        return False

@app.route('/api/zones', methods=['GET', 'PUT'])
def zones_endpoint():
    """Get or update zone definitions with speech text and video URLs"""
    if request.method == 'GET':
        zones = load_zones()
        return jsonify(zones)
    elif request.method == 'PUT':
        try:
            zones = request.json
            if not isinstance(zones, list):
                return jsonify({'error': 'Invalid zones format, expected array'}), 400
            if save_zones(zones):
                logger.info(f'Zones saved successfully ({len(zones)} zones)')
                return jsonify({'success': True, 'message': 'Zones saved successfully'})
            else:
                return jsonify({'error': 'Failed to save zones'}), 500
        except Exception as e:
            logger.error(f'Error saving zones: {e}')
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
    # Get AMR IP dynamically from settings
    amr_ip = get_amr_ip()
    amr_base_url = f'http://{amr_ip}'
    url = f'{amr_base_url}/{endpoint}'

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
    print(f'AMR Navigation System: http://{get_amr_ip()}')
    print(f'Proxy listening on http://0.0.0.0:5020')
    app.run(host='0.0.0.0', port=5020, debug=False)
