import { useEffect, useRef, useState } from 'react';

export default function LocationMap({ address, latitude, longitude, label = '위치 지도' }) {
  const mapRef = useRef(null);
  const [mapError, setMapError] = useState('');

  useEffect(() => {
    const mapElement = mapRef.current;
    let cancelled = false;
    let timer = null;
    let map = null;
    let marker = null;
    let attempts = 0;

    setMapError('');

    const cleanAddress = String(address ?? '').trim();

    const lat = Number(latitude);
    const lng = Number(longitude);

    const hasCoordinates =
      latitude !== null &&
      latitude !== undefined &&
      String(latitude).trim() !== '' &&
      longitude !== null &&
      longitude !== undefined &&
      String(longitude).trim() !== '' &&
      Number.isFinite(lat) &&
      Number.isFinite(lng) &&
      lat >= -90 &&
      lat <= 90 &&
      lng >= -180 &&
      lng <= 180 &&
      (lat !== 0 || lng !== 0);

    const initializeMap = () => {
      if (cancelled || !mapRef.current) return;

      const kakao = window.kakao;

      if (!kakao?.maps?.load) {
        if (attempts++ < 100) {
          timer = window.setTimeout(initializeMap, 100);
        } else {
          setMapError(
              '지도를 불러오지 못했습니다. 아래 주소나 카카오맵에서 장소를 확인해 주세요.'
          );
        }
        return;
      }

      kakao.maps.load(() => {
        if (cancelled || !mapRef.current) return;

        // 지도를 새로 만들기 전에 기존 DOM을 정리합니다.
        mapRef.current.innerHTML = '';

        // 주소 검색 전에는 전국이 보이는 위치를 사용합니다.
        const initialPosition = hasCoordinates
          ? new kakao.maps.LatLng(lat, lng)
          : new kakao.maps.LatLng(36.5, 127.8);

        map = new kakao.maps.Map(mapRef.current, {
          center: initialPosition,
          level: 3,
        });

        const showLocation = (position) => {
          if (cancelled || !map) return;

          marker = new kakao.maps.Marker({
            position,
            map,
          });

          map.setCenter(position);
          map.setLevel(3);
        };

        const showSavedCoordinates = () => {
          if (hasCoordinates) {
            showLocation(initialPosition);
            setMapError(
              '주소 검색에 실패하여 저장된 좌표를 표시하고 있습니다. 실제 위치와 일치하는지 확인해 주세요.'
            );
          } else {
            setMapError(
              '주소를 찾지 못했습니다. 아래 주소나 카카오맵에서 장소를 확인해 주세요.'
            );
          }
        };

        // 주소가 있으면 주소 검색 결과를 우선 사용합니다.
        if (cleanAddress) {
          if (!kakao.maps.services?.Geocoder) {
            setMapError(
              '지도에서 주소를 검색하지 못했습니다. 아래 주소를 확인해 주세요.'
            );
            showSavedCoordinates();
            return;
          }

          const geocoder =
            new kakao.maps.services.Geocoder();

          geocoder.addressSearch(
            cleanAddress,
            (result, status) => {
              if (cancelled) return;

              if (
                status === kakao.maps.services.Status.OK &&
                result?.length
              ) {
                // 검색 결과의 좌표가 유효한지 확인합니다.
                const resultLat = Number(result[0].y);
                const resultLng = Number(result[0].x);

                if (
                  Number.isFinite(resultLat) &&
                  Number.isFinite(resultLng) &&
                  resultLat >= -90 &&
                  resultLat <= 90 &&
                  resultLng >= -180 &&
                  resultLng <= 180
                ) {
                  showLocation(
                    new kakao.maps.LatLng(resultLat, resultLng)
                  );
                  return;
                }
              }

              showSavedCoordinates();
            }
          );

          return;
        }

        // 주소가 없을 때만 저장된 좌표를 사용합니다.
        if (hasCoordinates) {
          showLocation(initialPosition);
        } else {
          setMapError(
            '주소 또는 유효한 위치 좌표가 등록되지 않았습니다.'
          );
        }
      });
    };

    initializeMap();

    return () => {
      cancelled = true;

      if (timer !== null) {
        window.clearTimeout(timer);
      }

      if (marker) {
        marker.setMap(null);
      }

      // 다음 초기화를 위해 지도 DOM을 정리합니다.
      if (mapElement) {
        mapElement.innerHTML = '';
      }

      map = null;
      marker = null;
    };
  }, [address, latitude, longitude]);

  return (
    <div
      className="company-map-wrapper"
      style={{ margin: '20px 0' }}
    >
      <div
        ref={mapRef}
        role="region"
        aria-label={label}
        hidden={Boolean(mapError) && !latitude && !longitude}
        style={{
          width: '100%',
          height: '280px',
          borderRadius: '8px',
          backgroundColor: '#f0f0f0',
          border: '1px solid #ddd',
        }}
      />

      <p
        className="muted"
        style={{
          fontSize: '13px',
          marginTop: '8px',
          color: '#666',
        }}
      >
        📍 {address || '주소 정보가 없습니다.'}
      </p>
      {address?.trim() && <a href={`https://map.kakao.com/link/search/${encodeURIComponent(address.trim())}`} target="_blank" rel="noopener noreferrer">카카오맵에서 장소 확인 ↗</a>}

      {mapError && (
        <p
          role="alert"
          style={{
            color: '#c0392b',
            fontSize: '13px',
          }}
        >
          {mapError}
        </p>
      )}
    </div>
  );
}
