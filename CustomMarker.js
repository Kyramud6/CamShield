import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { getFirestore, collection, addDoc, getDocs, doc, deleteDoc } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

// ✅ Firebase Config
const firebaseConfig = {
  apiKey: "AIzaSyDOaqfyMNW96fIeFxvXdZgzrnQ-bDZPgbU",
  authDomain: "camshield-50aec.firebaseapp.com",
  projectId: "camshield-50aec",
  storageBucket: "camshield-50aec.firebasestorage.app",
  messagingSenderId: "655472977777",
  appId: "1:655472977777:web:907fe040370301c08b78d0",
  measurementId: "G-W4N2BLCEL3"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

// ✅ Mapbox Init
mapboxgl.accessToken = "pk.eyJ1Ijoia3NsYWkiLCJhIjoiY21mbDVtcHIxMDFzNzJtczhtaG9yNXJ1eCJ9.Fqc91L0H0ZQbz-1VycVEGg";

const map = new mapboxgl.Map({
  container: "map",
  style: "mapbox://styles/kslai/cmfjvs9v1005z01s468fy5ws9",
  center: [101.758130772282, 2.81133705],
  zoom: 15
});

// State
const markersMap = {};
let rightClickLatLng = null;
let selectedMarkerId = null;

const contextMenu = document.getElementById("contextMenu");
const addPinBtn = document.getElementById("addPin");
const deletePinBtn = document.getElementById("deletePin");

// Hide context menu on left-click elsewhere
map.getCanvas().addEventListener("click", () => contextMenu.style.display = "none");

// ✅ Load saved markers
async function loadMarkers() {
  const querySnapshot = await getDocs(collection(db, "Markers"));
  querySnapshot.forEach((docSnap) => {
    const data = docSnap.data();
    addMarkerToMap(docSnap.id, data.lat, data.lng, data.type);
  });
}

function addMarkerToMap(id, lat, lng, type) {
  const el = document.createElement("div");
  el.className = "marker";
  el.style.width = "20px";
  el.style.height = "20px";
  el.style.background = "red";
  el.style.borderRadius = "50%";
  el.style.border = "2px solid white";

  const marker = new mapboxgl.Marker(el)
    .setLngLat([lng, lat])
    .setPopup(new mapboxgl.Popup().setText(type))
    .addTo(map);

  markersMap[id] = marker;

  // Left-click → select marker
  el.addEventListener("click", (e) => {
    e.stopPropagation();

    // Reset styles
    Object.keys(markersMap).forEach(mid => {
      markersMap[mid].getElement().classList.remove("selected-marker");
    });

    // Mark selected
    selectedMarkerId = id;
    el.classList.add("selected-marker");

    // Show context menu (delete enabled)
    contextMenu.style.top = e.pageY + "px";
    contextMenu.style.left = e.pageX + "px";
    addPinBtn.disabled = true;
    deletePinBtn.disabled = false;
    contextMenu.style.display = "block";
  });
}

// Right-click on map → context menu for add
map.on("contextmenu", (e) => {
  e.preventDefault();
  rightClickLatLng = e.lngLat;
  selectedMarkerId = null; // not from marker selection
  contextMenu.style.top = e.originalEvent.pageY + "px";
  contextMenu.style.left = e.originalEvent.pageX + "px";
  addPinBtn.disabled = false;
  deletePinBtn.disabled = true;
  contextMenu.style.display = "block";
});

// Handle Drop Pin
addPinBtn.addEventListener("click", async () => {
  if (!rightClickLatLng) return;
  const markerType = document.getElementById("markerType").value;
  const lat = rightClickLatLng.lat;
  const lng = rightClickLatLng.lng;

  const docRef = await addDoc(collection(db, "Markers"), {
    type: markerType,
    lat,
    lng,
    timestamp: new Date()
  });

  addMarkerToMap(docRef.id, lat, lng, markerType);
  contextMenu.style.display = "none";
});

// Handle Delete Pin
deletePinBtn.addEventListener("click", async () => {
  if (!selectedMarkerId) return;
  await deleteDoc(doc(db, "Markers", selectedMarkerId));
  markersMap[selectedMarkerId].remove();
  delete markersMap[selectedMarkerId];
  selectedMarkerId = null;
  contextMenu.style.display = "none";
});

// Load existing markers
loadMarkers();
