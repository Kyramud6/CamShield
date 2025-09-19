    import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
    import { getFirestore, collection, getDocs, doc, updateDoc } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

    const firebaseConfig = {
      apiKey: "AIzaSyDOaqfyMNW96fIeFxvXdZgzrnQ-bDZPgbU",
      authDomain: "camshield-50aec.firebaseapp.com",
      projectId: "camshield-50aec",
      storageBucket: "camshield-50aec.firebasestorage.app",
      messagingSenderId: "655472977777",
      appId: "1:655472977777:web:907fe040370301c08b78d0",
      measurementId: "G-W4N2BLCEL3"
    };

    // Initialize Firebase
    const app = initializeApp(firebaseConfig);
    const db = getFirestore(app);

    // Fetch SOS Requests
    async function loadSOS() {
      const querySnapshot = await getDocs(collection(db, "SOS"));
      const sosList = document.getElementById("SOS");
      sosList.innerHTML = "";

      querySnapshot.forEach((docSnap) => {
        const data = docSnap.data();

        // Format location
        let loc = "N/A";
        if (data.location && data.location.latitude !== undefined && data.location.longitude !== undefined) {
          loc = `${data.location.latitude}, ${data.location.longitude}`;
        }

        // Format timestamp
        let timeStr = "N/A";
        if (data.timestamp && data.timestamp.toDate) {
          timeStr = data.timestamp.toDate().toLocaleString();
        }

        const sosItem = document.createElement("div");
        sosItem.classList.add("SOS");

        sosItem.innerHTML = `
          <p><strong>User ID:</strong> ${data.userid || "N/A"}</p>
          <p><strong>Name:</strong> ${data.name || "N/A"}</p>
          <p><strong>Location:</strong> ${loc}</p>
          <p><strong>Type:</strong> ${data.type || "N/A"}</p>
          <p><strong>Status:</strong> ${data.status || "N/A"}</p>
          <p><strong>Time:</strong> ${timeStr}</p>
          <button onclick="playAudio('${data.audioUrl || ""}')">🎧 Play Audio</button>
          <button onclick="updateStatus('${docSnap.id}')">✅ Respond</button>
          <hr>
        `;

        sosList.appendChild(sosItem);
      });
    }

    // Play Audio
    window.playAudio = function(url) {
      if (!url) {
        alert("No audio uploaded yet for this SOS.");
        return;
      }
      const audio = new Audio(url);
      audio.play();
    }

    // Update status to 'responded'
    window.updateStatus = async function(docId) {
      const sosDoc = doc(db, "SOS", docId);
      try {
        await updateDoc(sosDoc, { status: "responded" });
        alert("Status updated to responded!");
        loadSOS(); // Refresh the list
      } catch (err) {
        console.error("Error updating status:", err);
        alert("Failed to update status.");
      }
    }

    // Load on page start
window.onload = () => {
  const sosList = document.getElementById("SOS");
  if (sosList) loadSOS();
};

    