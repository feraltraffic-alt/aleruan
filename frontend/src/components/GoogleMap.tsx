import { useEffect, useRef, useState } from 'react';
import { api, type ClientConfig } from '../api';
import { useI18n } from '../i18n';
import { FALLBACK_MAP_ID, loadGoogleMaps, onMapsAuthFailure } from '../maps';
import { useLoad } from './useLoad';

export interface MapPin {
  id: string;
  lat: number;
  lng: number;
  title: string;
  /** Pin colour token: ok, warn, risk, idle. */
  tone: 'ok' | 'warn' | 'risk' | 'idle';
  /** Plain-text lines for the info window. */
  lines?: string[];
  href?: string;
}

const TONES = { ok: '#1f7a4d', warn: '#a8480a', risk: '#b42318', idle: '#8a8494' };
const EUROPE = { lat: 50.6, lng: 14.5 };

type State = 'loading' | 'ready' | 'no-key' | 'failed' | 'rejected';

/** Shared state of the Maps script: key from the server, load status. */
function useMaps() {
  const { lang } = useI18n();
  const cfg = useLoad(() => api.config(), []);
  const [script, setScript] = useState<'loading' | 'ready' | 'failed'>('loading');
  const [rejected, setRejected] = useState(false);
  const key = cfg.data?.googleMapsApiKey ?? null;

  useEffect(() => onMapsAuthFailure(() => setRejected(true)), []);
  useEffect(() => {
    if (!key) return;
    let cancelled = false;
    loadGoogleMaps(key, lang)
      .then(() => { if (!cancelled) setScript('ready'); })
      .catch(() => { if (!cancelled) setScript('failed'); });
    return () => { cancelled = true; };
  }, [key, lang]);

  const state: State = cfg.loading ? 'loading' : cfg.error ? 'failed' : !key ? 'no-key' : rejected ? 'rejected' : script;
  return { state, config: cfg.data as ClientConfig | null };
}

/** No map will appear: collapse the box to the notice. */
const off = (s: State) => s === 'no-key' || s === 'failed' || s === 'rejected';

function MapNotice({ state }: { state: State }) {
  const { t } = useI18n();
  if (state === 'ready') return null;
  const key = state === 'loading' ? 'map.loading' : state === 'no-key' ? 'map.noKey'
    : state === 'rejected' ? 'map.rejected' : 'map.failed';
  return <div className="map-notice" role={state === 'loading' ? 'status' : 'note'}>{t(key)}</div>;
}

/** Sites on a Google map; falls back to a notice when no key is configured. */
export function SitesMap({ pins, onSelect, selectedId }: { pins: MapPin[]; onSelect?(id: string): void; selectedId?: string | null }) {
  const { state, config } = useMaps();
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<google.maps.Map | null>(null);
  const markers = useRef<google.maps.marker.AdvancedMarkerElement[]>([]);
  const info = useRef<google.maps.InfoWindow | null>(null);

  useEffect(() => {
    if (state !== 'ready' || !box.current) return;
    let cancelled = false;
    (async () => {
      const { Map, InfoWindow } = await google.maps.importLibrary('maps') as google.maps.MapsLibrary;
      const { AdvancedMarkerElement, PinElement } = await google.maps.importLibrary('marker') as google.maps.MarkerLibrary;
      if (cancelled || !box.current) return;
      map.current ??= new Map(box.current, {
        center: EUROPE, zoom: 5, mapId: config?.googleMapsMapId ?? FALLBACK_MAP_ID,
        streetViewControl: false, mapTypeControl: false, fullscreenControl: true,
      });
      info.current ??= new InfoWindow();
      markers.current.forEach((m) => { m.map = null; });
      const bounds = new google.maps.LatLngBounds();
      markers.current = pins.map((p) => {
        const pin = new PinElement({ background: TONES[p.tone], borderColor: '#ffffff', glyphColor: '#ffffff',
          scale: p.id === selectedId ? 1.25 : 1 });
        const marker = new AdvancedMarkerElement({ map: map.current, position: { lat: p.lat, lng: p.lng },
          title: p.title, content: pin.element, gmpClickable: true });
        marker.addListener('click', () => {
          const el = document.createElement('div');
          el.className = 'map-info';
          const h = document.createElement(p.href ? 'a' : 'strong');
          h.textContent = p.title;
          if (p.href && h instanceof HTMLAnchorElement) h.href = p.href;
          el.appendChild(h);
          (p.lines ?? []).forEach((line) => { const d = document.createElement('div'); d.textContent = line; el.appendChild(d); });
          info.current!.setContent(el);
          info.current!.open({ map: map.current!, anchor: marker });
          onSelect?.(p.id);
        });
        bounds.extend({ lat: p.lat, lng: p.lng });
        return marker;
      });
      if (pins.length === 1) { map.current.setCenter({ lat: pins[0].lat, lng: pins[0].lng }); map.current.setZoom(11); }
      else if (pins.length > 1) map.current.fitBounds(bounds, 48);
    })();
    return () => { cancelled = true; };
  }, [state, pins, selectedId, onSelect, config]);

  return (
    <div className={`map-wrap${off(state) ? ' off' : ''}`}>
      <div ref={box} className="map" aria-label="Map" />
      <MapNotice state={state} />
    </div>
  );
}

export interface GeoResult { lat: number; lng: number; address?: string; city?: string; country?: string }

/**
 * Small map for one site: geocodes the typed address and lets the user drag the pin to the exact spot.
 */
export function PinPicker({ value, query, onChange }: {
  value: { lat: number; lng: number } | null; query: string; onChange(r: GeoResult): void;
}) {
  const { t } = useI18n();
  const { state, config } = useMaps();
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<google.maps.Map | null>(null);
  const marker = useRef<google.maps.marker.AdvancedMarkerElement | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const change = useRef(onChange);
  useEffect(() => { change.current = onChange; }, [onChange]);

  useEffect(() => {
    if (state !== 'ready' || !box.current) return;
    let cancelled = false;
    (async () => {
      const { Map } = await google.maps.importLibrary('maps') as google.maps.MapsLibrary;
      const { AdvancedMarkerElement } = await google.maps.importLibrary('marker') as google.maps.MarkerLibrary;
      if (cancelled || !box.current) return;
      const center = value ?? EUROPE;
      map.current ??= new Map(box.current, { center, zoom: value ? 15 : 4,
        mapId: config?.googleMapsMapId ?? FALLBACK_MAP_ID, streetViewControl: false, mapTypeControl: false });
      if (!marker.current) {
        marker.current = new AdvancedMarkerElement({ map: map.current, position: value, gmpDraggable: true });
        marker.current.addListener('dragend', () => {
          const p = marker.current!.position as google.maps.LatLngLiteral;
          change.current({ lat: Math.round(p.lat * 1e6) / 1e6, lng: Math.round(p.lng * 1e6) / 1e6 });
        });
        map.current.addListener('click', (e: google.maps.MapMouseEvent) => {
          if (!e.latLng) return;
          marker.current!.position = e.latLng;
          change.current({ lat: Math.round(e.latLng.lat() * 1e6) / 1e6, lng: Math.round(e.latLng.lng() * 1e6) / 1e6 });
        });
      } else if (value) {
        marker.current.position = value;
      }
    })();
    return () => { cancelled = true; };
  }, [state, value, config]);

  const find = async () => {
    if (!query.trim()) { setMessage(t('map.typeAddress')); return; }
    setBusy(true);
    setMessage(null);
    try {
      const { Geocoder } = await google.maps.importLibrary('geocoding') as google.maps.GeocodingLibrary;
      const { results } = await new Geocoder().geocode({ address: query });
      const r = results[0];
      if (!r) { setMessage(t('map.notFound')); return; }
      const part = (type: string, short = false) => {
        const c = r.address_components.find((x) => x.types.includes(type));
        return c ? (short ? c.short_name : c.long_name) : undefined;
      };
      const loc = { lat: Math.round(r.geometry.location.lat() * 1e6) / 1e6, lng: Math.round(r.geometry.location.lng() * 1e6) / 1e6 };
      map.current?.setCenter(loc);
      map.current?.setZoom(16);
      if (marker.current) marker.current.position = loc;
      onChange({ ...loc, city: part('locality') ?? part('postal_town'), country: part('country', true) });
      setMessage(t('map.found'));
    } catch {
      setMessage(t('map.notFound'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="pin-picker">
      <div className={`map-wrap small${off(state) ? ' off' : ''}`}>
        <div ref={box} className="map" />
        <MapNotice state={state} />
      </div>
      {state === 'ready' && (
        <div className="pin-actions">
          <button type="button" className="btn" onClick={find} disabled={busy}>{t('map.findAddress')}</button>
          <span className="sub">{message ?? t('map.dragHint')}</span>
        </div>
      )}
    </div>
  );
}
