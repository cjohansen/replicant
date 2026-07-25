document.body.addEventListener("click", function (e) {
  if (!window.replicantHydration) {
    const actions = e.composedPath()[0].dataset.replicantClick;
    if (actions) {
      console.log(actions);
      fetch("/actions", {
        method: "POST",
        body: actions
      }).then(res => {
        location.reload();
      });
    }
  }
});
