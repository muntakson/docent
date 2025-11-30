#!/usr/bin/env python3
"""
AMR API Proxy Server
Forwards requests from the web UI to the AMR navigation system
to avoid CORS issues
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import requests
import logging

app = Flask(__name__)
CORS(app)  # Enable CORS for all routes

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# AMR Navigation System IP (can be changed via environment variable)
import os
AMR_IP = os.environ.get('AMR_IP', '192.168.0.5')
AMR_BASE_URL = f'http://{AMR_IP}'

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({'status': 'ok', 'amr_ip': AMR_IP})

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
            response = requests.delete(url, json=request.json, timeout=5)
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
