const React = require('react');

class Footer extends React.Component {
  render() {
    return (
      <footer className="nav-footer" id="footer">
        <hr className="separator" />
        <section className="copyright">
          {this.props.config.copyright}
        </section>
      </footer>
    );
  }
}

module.exports = Footer;
