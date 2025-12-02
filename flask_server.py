
from flask import Flask, request, jsonify
from flask_cors import CORS
from PIL import Image
import io
import os

app = Flask(__name__)
CORS(app)  # CORS 허용

UPLOAD_FOLDER = 'uploads'
RESULT_FOLDER = 'results'

os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(RESULT_FOLDER, exist_ok=True)

@app.route('/upload', methods=['POST'])
def upload():
    if 'image' not in request.files:
        return jsonify({'error': 'No image provided'}), 400
    image = request.files['image']
    image_path = os.path.join(UPLOAD_FOLDER, image.filename)
    image.save(image_path)

    # 예시: 받아온 이미지를 흑백으로 변환하여 저장
    img = Image.open(image_path).convert('L')
    result_path = os.path.join(RESULT_FOLDER, f"result_{image.filename}")
    img.save(result_path)

    return jsonify({'result_path': result_path})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
